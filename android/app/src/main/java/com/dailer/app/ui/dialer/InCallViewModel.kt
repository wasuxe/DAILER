package com.dailer.app.ui.dialer

import android.app.Application
import android.media.AudioManager
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.dailer.app.DailerApplication
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.data.local.entity.RelativeEntity
import com.dailer.app.service.CallState
import com.dailer.app.service.DailerInCallService
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InCallViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DailerApplication).repository
    private val audioManager = application.getSystemService(AudioManager::class.java)

    val callState: LiveData<Int> = DailerInCallService.callState.map { it.state }
    val callerNumber: LiveData<String> = DailerInCallService.callState.map { it.phoneNumber }
    val recentRecords: LiveData<List<CallRecordEntity>> = repository.getAllCallRecords().asLiveData()

    // Preserve the last-known caller number so verdict lookup works after disconnect
    private var lastKnownCallerNumber: String = ""

    // Verification timeout: set to true if no verdict arrives within 20s of call ending
    private val _verificationTimedOut = MutableLiveData(false)
    val verificationTimedOut: LiveData<Boolean> = _verificationTimedOut
    private var verificationTimeoutJob: Job? = null

    private val _callerRelative = MutableLiveData<RelativeEntity?>(null)
    val callerRelative: LiveData<RelativeEntity?> = _callerRelative

    private val _callDurationSeconds = MutableLiveData(0L)
    val callDurationSeconds: LiveData<Long> = _callDurationSeconds

    private val _isMuted = MutableLiveData(false)
    val isMuted: LiveData<Boolean> = _isMuted

    private val _isSpeakerOn = MutableLiveData(false)
    val isSpeakerOn: LiveData<Boolean> = _isSpeakerOn

    private var timerJob: Job? = null

    private val callStateObserver = Observer<CallState> { callStateObj ->
        if (callStateObj == null) return@Observer
        val normalized = AudioUtils.normalizePhoneNumber(callStateObj.phoneNumber)
        // Preserve phone number while call is active or just connected
        if (normalized.isNotBlank()) {
            lastKnownCallerNumber = normalized
        }
        viewModelScope.launch {
            _callerRelative.value = if (normalized.isNotBlank()) {
                repository.getRelativeByPhone(normalized)
            } else {
                null
            }
        }
        if (callStateObj.state == Call.STATE_ACTIVE) {
            startCallTimer()
            _verificationTimedOut.value = false
            verificationTimeoutJob?.cancel()
        } else if (callStateObj.state == Call.STATE_DISCONNECTED) {
            stopCallTimer()
            startVerificationTimeout()
        }
    }

    init {
        DailerInCallService.callState.observeForever(callStateObserver)
    }

    private fun startCallTimer() {
        if (timerJob?.isActive == true) return
        _callDurationSeconds.value = 0L
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _callDurationSeconds.value = (_callDurationSeconds.value ?: 0L) + 1L
            }
        }
    }

    private fun stopCallTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun answerCall() {
        DailerInCallService.currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun declineCall() {
        val call = DailerInCallService.currentCall ?: return
        if (call.state == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }

    fun hangupCall() {
        DailerInCallService.currentCall?.disconnect()
    }

    fun toggleMute() {
        val next = !(_isMuted.value ?: false)
        _isMuted.value = next
        audioManager?.isMicrophoneMute = next
    }

    fun toggleSpeaker() {
        val next = !(_isSpeakerOn.value ?: false)
        _isSpeakerOn.value = next
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = next
    }

    fun latestRecordForCaller(records: List<CallRecordEntity>, phone: String): CallRecordEntity? {
        val effectivePhone = phone.ifBlank { lastKnownCallerNumber }
        val normalized = AudioUtils.normalizePhoneNumber(effectivePhone)
        if (normalized.isBlank()) return null
        return records.firstOrNull {
            AudioUtils.normalizePhoneNumber(it.phoneNumber) == normalized
        }
    }

    fun latestVerdictForCaller(records: List<CallRecordEntity>, phone: String): String {
        // Use preserved number if the live number is already cleared
        val effectivePhone = phone.ifBlank { lastKnownCallerNumber }
        val normalized = AudioUtils.normalizePhoneNumber(effectivePhone)
        if (normalized.isBlank()) return Constants.VERDICT_ANALYZING
        val match = records.firstOrNull {
            AudioUtils.normalizePhoneNumber(it.phoneNumber) == normalized
        }
        val verdict = match?.verdict ?: Constants.VERDICT_ANALYZING
        // If verdict arrived, cancel the timeout
        if (verdict != Constants.VERDICT_ANALYZING && verdict != Constants.VERDICT_PENDING) {
            verificationTimeoutJob?.cancel()
            _verificationTimedOut.value = false
        }
        return verdict
    }

    /** Returns the preserved caller number even after disconnect */
    fun getEffectiveCallerNumber(): String {
        return callerNumber.value?.ifBlank { lastKnownCallerNumber } ?: lastKnownCallerNumber
    }

    /** Start a 20-second timeout; if verdict is still PENDING, signal timeout */
    private fun startVerificationTimeout() {
        verificationTimeoutJob?.cancel()
        verificationTimeoutJob = viewModelScope.launch {
            delay(20_000L)
            // Check if verdict is still pending
            val records = recentRecords.value.orEmpty()
            val effectivePhone = getEffectiveCallerNumber()
            val verdict = latestVerdictForCaller(records, effectivePhone)
            if (verdict == Constants.VERDICT_ANALYZING || verdict == Constants.VERDICT_PENDING) {
                _verificationTimedOut.value = true
            }
        }
    }

    fun isRinging(state: Int): Boolean = state == Call.STATE_RINGING

    override fun onCleared() {
        stopCallTimer()
        DailerInCallService.callState.removeObserver(callStateObserver)
        super.onCleared()
    }
}
