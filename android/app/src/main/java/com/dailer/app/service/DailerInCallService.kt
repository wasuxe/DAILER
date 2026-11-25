package com.dailer.app.service

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants
import java.util.UUID

data class CallState(
    val phoneNumber: String = "",
    val callerName: String = "",
    val state: Int = Call.STATE_DISCONNECTED,
    val startTime: Long = 0L,
    val isIncoming: Boolean = false
)

class DailerInCallService : InCallService() {

    private var activeCallId: String? = null
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")

            val previous = callState.value ?: CallState()
            val startTime = when {
                state == Call.STATE_ACTIVE && previous.startTime == 0L -> System.currentTimeMillis()
                else -> previous.startTime
            }

            callState.postValue(
                previous.copy(
                    state = state,
                    startTime = startTime
                )
            )

            when (state) {
                Call.STATE_RINGING -> {
                    Log.i(TAG, "Call ringing from ${previous.phoneNumber}")
                }
                Call.STATE_ACTIVE -> {
                    Log.i(TAG, "Call active with ${previous.phoneNumber}")
                }
                Call.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Call disconnected")
                    stopRecordingService()
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            val phoneNumber = extractPhoneNumber(call)
            val callerName = extractCallerName(call, phoneNumber)
            val previous = callState.value ?: CallState()
            callState.postValue(
                previous.copy(
                    phoneNumber = phoneNumber,
                    callerName = callerName
                )
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        activeCallId = UUID.randomUUID().toString()
        isMuted = false
        isSpeakerOn = false

        val phoneNumber = extractPhoneNumber(call)
        val callerName = extractCallerName(call, phoneNumber)
        val isIncoming = isIncomingCall(call)
        val startTime = if (call.state == Call.STATE_ACTIVE) System.currentTimeMillis() else 0L

        call.registerCallback(callCallback)

        callState.postValue(
            CallState(
                phoneNumber = phoneNumber,
                callerName = callerName,
                state = call.state,
                startTime = startTime,
                isIncoming = isIncoming
            )
        )

        startRecordingService(phoneNumber, isIncoming)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)

        if (currentCall == call) {
            val previous = callState.value ?: CallState()
            currentCall = null
            activeCallId = null
            callState.postValue(previous.copy(state = Call.STATE_DISCONNECTED))
            stopRecordingService()
        }
    }

    fun answerCall() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectCall() {
        currentCall?.reject(false, null)
    }

    fun hangupCall() {
        currentCall?.disconnect()
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        setMuted(isMuted)
        return isMuted
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker(): Boolean {
        val currentRoute = callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val enableSpeaker = currentRoute != CallAudioState.ROUTE_SPEAKER
        setAudioRoute(
            if (enableSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
        isSpeakerOn = enableSpeaker
        return enableSpeaker
    }

    private fun startRecordingService(phoneNumber: String, isIncoming: Boolean) {
        val callId = activeCallId ?: UUID.randomUUID().toString()
        val direction = if (isIncoming) {
            Constants.CALL_DIRECTION_INCOMING
        } else {
            Constants.CALL_DIRECTION_OUTGOING
        }

        val intent = Intent(this, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_START
            putExtra(Constants.EXTRA_CALL_ID, callId)
            putExtra(Constants.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(Constants.EXTRA_CALL_DIRECTION, direction)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecordingService() {
        val intent = Intent(this, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_STOP
        }
        startService(intent)
    }

    private fun extractPhoneNumber(call: Call): String {
        val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
        return if (raw.isNotBlank()) AudioUtils.normalizePhoneNumber(raw) else ""
    }

    private fun extractCallerName(call: Call, fallbackNumber: String): String {
        val displayName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
        if (displayName != null) return displayName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contactName = call.details?.contactDisplayName?.takeIf { it.isNotBlank() }
            if (contactName != null) return contactName
        }
        return fallbackNumber
    }

    private fun isIncomingCall(call: Call): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }
    }

    companion object {
        private const val TAG = "DailerInCallService"

        val callState: MutableLiveData<CallState> = MutableLiveData(CallState())

        var instance: DailerInCallService? = null
            private set

        var currentCall: Call? = null
            private set
    }
}
