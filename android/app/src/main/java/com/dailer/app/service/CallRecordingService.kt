package com.dailer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.longPreferencesKey
import com.dailer.app.DailerApplication
import com.dailer.app.R
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants
import com.dailer.app.util.dailerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CallRecordingService : Service(), AudioRecorder.Listener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var audioRecorder: AudioRecorder
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeCallId: String? = null
    private var activePhoneNumber: String = ""
    private var activeCallDirection: String = Constants.CALL_DIRECTION_INCOMING
    private var activeRelativeId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioRecorder = AudioRecorder(this, this)

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val activeCall = DailerInCallService.currentCall
            if (activeCall == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            val state = DailerInCallService.callState.value
            val phone = state?.phoneNumber.orEmpty()
            val direction = if (state?.isIncoming == true) {
                Constants.CALL_DIRECTION_INCOMING
            } else {
                Constants.CALL_DIRECTION_OUTGOING
            }
            startForegroundNotification()
            beginRecording(UUID.randomUUID().toString(), phone, direction, null)
            return START_STICKY
        }

        if (intent.action == ACTION_STOP) {
            finalizeActiveRecording()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: UUID.randomUUID().toString()
        val phoneNumber = intent.getStringExtra(Constants.EXTRA_PHONE_NUMBER).orEmpty()
        val callDirection = intent.getStringExtra(Constants.EXTRA_CALL_DIRECTION)
            ?: Constants.CALL_DIRECTION_INCOMING
        val relativeId = intent.getStringExtra(Constants.EXTRA_RELATIVE_ID)

        startForegroundNotification()

        if (audioRecorder.isRecording() && activeCallId == callId) {
            return START_STICKY
        }

        if (audioRecorder.isRecording()) {
            finalizeActiveRecording()
        }

        beginRecording(callId, phoneNumber, callDirection, relativeId)
        return START_STICKY
    }

    private fun beginRecording(
        callId: String,
        phoneNumber: String,
        callDirection: String,
        relativeId: String?
    ) {
        activeCallId = callId
        activePhoneNumber = phoneNumber
        activeCallDirection = callDirection
        activeRelativeId = relativeId

        serviceScope.launch {
            val bufferDurationMs = readBufferDurationMs()
            acquireWakeLock(bufferDurationMs + WAKE_LOCK_BUFFER_MS)

            val outputFile = File(
                cacheDir,
                "call_${callId}_${System.currentTimeMillis()}.m4a"
            )
            val started = audioRecorder.start(outputFile, bufferDurationMs)
            if (!started) {
                Log.e(TAG, "Failed to start recording for callId=$callId")
                releaseWakeLock()
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun readBufferDurationMs(): Long {
        return runCatching {
            val key = longPreferencesKey(Constants.PREF_BUFFER_DURATION_MS)
            applicationContext.dailerDataStore.data
                .map { prefs -> prefs[key] ?: Constants.DEFAULT_BUFFER_DURATION_MS }
                .first()
                .coerceAtLeast(1_000L)
        }.getOrDefault(Constants.DEFAULT_BUFFER_DURATION_MS)
    }

    override fun onRecordingComplete(file: File) {
        Log.i(TAG, "Auto-stop recording complete: ${file.name} (${file.length()} bytes)")
        releaseWakeLock()
        persistAndEnqueue(file)
        stopForegroundCompat()
        stopSelf()
    }

    override fun onRecordingError(error: Throwable) {
        Log.e(TAG, "Recording error: ${error.message}", error)
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private fun finalizeActiveRecording() {
        val file = audioRecorder.stop()
        releaseWakeLock()
        if (file != null && file.exists() && file.length() > 0L) {
            persistAndEnqueue(file)
        }
    }

    private fun persistAndEnqueue(file: File) {
        val callId = activeCallId ?: UUID.randomUUID().toString()
        val phoneNumber = activePhoneNumber
        val direction = activeCallDirection
        val relativeId = activeRelativeId

        persistenceScope.launch {
            val repository = (application as DailerApplication).repository
            val resolvedRelativeId = relativeId ?: if (phoneNumber.isNotBlank()) {
                repository.findRelativeByPhone(AudioUtils.normalizePhoneNumber(phoneNumber))?.enrollmentId
            } else {
                null
            }

            val durationMs = AudioUtils.getAudioDurationMs(file)
            val record = CallRecordEntity(
                callId = callId,
                phoneNumber = phoneNumber,
                relativeId = resolvedRelativeId,
                verdict = Constants.VERDICT_PENDING,
                timestamp = System.currentTimeMillis(),
                audioPath = file.absolutePath,
                callDirection = direction,
                durationMs = durationMs,
                isUploaded = false
            )
            repository.insertCallRecord(record)
            UploadWorker.enqueue(applicationContext)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.RECORDING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(Constants.RECORDING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_recording_text)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(R.drawable.ic_phone)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        runCatching {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    override fun onDestroy() {
        finalizeActiveRecording()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CallRecordingService"
        private const val WAKE_LOCK_TAG = "Dailer::CallRecordingWakeLock"
        private const val WAKE_LOCK_BUFFER_MS = 15_000L

        const val ACTION_START = Constants.ACTION_START_RECORDING
        const val ACTION_STOP = Constants.ACTION_STOP_RECORDING
    }
}
