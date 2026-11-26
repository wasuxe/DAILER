package com.dailer.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.dailer.app.DailerApplication
import com.dailer.app.R
import com.dailer.app.data.remote.dto.VerifyResponse
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as DailerApplication).repository
        val pendingRecords = repository.getPendingCallRecords()

        if (pendingRecords.isEmpty()) {
            return Result.success()
        }

        var hasNetworkError = false

        for (record in pendingRecords) {
            val audioPath = record.audioPath
            val audioFile = audioPath?.let { File(it) }

            if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "Audio file missing for callId=${record.callId}; marking record processed")
                repository.updateCallRecord(
                    record.copy(
                        verdict = Constants.VERDICT_UNKNOWN,
                        reason = "Audio file missing",
                        isUploaded = true
                    )
                )
                continue
            }

            val audioBase64 = AudioUtils.fileToBase64(audioFile)
            if (audioBase64.isEmpty()) {
                audioFile.delete()
                repository.updateCallRecord(
                    record.copy(
                        verdict = Constants.VERDICT_UNKNOWN,
                        reason = "Empty audio recording",
                        isUploaded = true
                    )
                )
                continue
            }

            val normalizedPhone = if (record.phoneNumber.isNotBlank()) {
                AudioUtils.normalizePhoneNumber(record.phoneNumber)
            } else {
                ""
            }
            val relative = if (normalizedPhone.isNotBlank()) {
                repository.findRelativeByPhone(normalizedPhone)
            } else {
                null
            }
            val resolvedRelativeId = relative?.enrollmentId ?: record.relativeId

            val result = repository.verifyCall(
                audioBase64 = audioBase64,
                phoneNumber = normalizedPhone,
                claimedRelativeId = resolvedRelativeId,
                callRecordId = record.id
            )
            Log.i(TAG, "Upload started for callId=${record.callId}, audioSize=${audioFile.length()} bytes")

            result.fold(
                onSuccess = { response ->
                    Log.i(TAG, "API response for callId=${record.callId}: verdict=${response.verdict}, " +
                        "aletheia.isHuman=${response.aletheia?.isHuman}, aletheia.confidence=${response.aletheia?.confidence}, " +
                        "eidos.matchScore=${response.eidos?.matchScore}")
                    val confidence = response.aletheia?.confidence ?: 0f
                    val syntheticScore = if (response.aletheia?.isHuman == false) 1f else 0f
                    val speakerScore = response.eidos?.matchScore ?: 0f
                    val reason = response.message

                    repository.updateCallRecord(
                        record.copy(
                            relativeId = resolvedRelativeId,
                            verdict = response.verdict,
                            confidence = confidence,
                            syntheticScore = syntheticScore,
                            speakerScore = speakerScore,
                            reason = reason,
                            isUploaded = true
                        )
                    )
                    audioFile.delete()

                    if (isWarningVerdict(response)) {
                        showWarningNotification(
                            callerLabel = relative?.name ?: normalizedPhone.ifBlank { "Unknown caller" },
                            callId = record.callId
                        )
                    }

                    sendVerificationBroadcast(
                        callId = record.callId,
                        phoneNumber = normalizedPhone,
                        response = response
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Verification upload failed for callId=${record.callId}: ${error.message}", error)
                    if (isRetryableNetworkError(error) && runAttemptCount < 1) {
                        hasNetworkError = true
                    } else {
                        if (isRetryableNetworkError(error)) {
                            Log.e(TAG, "Verification API failed after retry for callId=${record.callId}; giving up.")
                        }
                        audioFile.delete()
                        repository.updateCallRecord(
                            record.copy(
                                verdict = Constants.VERDICT_UNKNOWN,
                                reason = error.message ?: "Verification error",
                                isUploaded = true
                            )
                        )
                    }
                }
            )
        }

        return if (hasNetworkError) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun isWarningVerdict(response: VerifyResponse): Boolean {
        return response.verdict.equals(Constants.VERDICT_SCAM_WARNING, ignoreCase = true) ||
            response.verdict.equals("warning", ignoreCase = true) ||
            response.aletheia?.isHuman == false
    }

    private fun isRetryableNetworkError(error: Throwable): Boolean {
        return when (error) {
            is IOException -> true
            is HttpException -> error.code() >= 500 || error.code() == 429
            else -> false
        }
    }

    private fun showWarningNotification(callerLabel: String, callId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureAlertNotificationChannel()

        val notification = NotificationCompat.Builder(
            applicationContext,
            Constants.ALERT_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(applicationContext.getString(R.string.banner_warning_title))
            .setContentText("Could not verify caller ($callerLabel)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Could not verify caller ($callerLabel). Synthetic speech or voice mismatch detected.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(Constants.WARNING_NOTIFICATION_ID + callId.hashCode(), notification)
    }

    private fun ensureAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(Constants.ALERT_NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.ALERT_NOTIFICATION_CHANNEL_ID,
                    "Voice Verification Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a caller's voice could not be verified"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun sendVerificationBroadcast(
        callId: String,
        phoneNumber: String,
        response: VerifyResponse
    ) {
        val intent = Intent(Constants.ACTION_VERIFICATION_RESULT).apply {
            setPackage(applicationContext.packageName)
            putExtra(Constants.EXTRA_CALL_ID, callId)
            putExtra(Constants.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(Constants.EXTRA_VERDICT, response.verdict)
            val confidence = response.aletheia?.confidence ?: 0f
            val syntheticScore = if (response.aletheia?.isHuman == false) 1f else 0f
            val speakerScore = response.eidos?.matchScore ?: 0f
            putExtra(Constants.EXTRA_CONFIDENCE, confidence)
            putExtra(Constants.EXTRA_SYNTHETIC_SCORE, syntheticScore)
            putExtra(Constants.EXTRA_SPEAKER_SCORE, speakerScore)
            putExtra(Constants.EXTRA_REASON, response.message)
        }
        applicationContext.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val UNIQUE_WORK_NAME = "dailer_pending_call_uploads"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
