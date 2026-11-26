package com.dailer.app.ui.enrollment

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dailer.app.DailerApplication
import com.dailer.app.service.AudioRecorder
import com.dailer.app.util.AudioUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class EnrollViewModel(application: Application) : AndroidViewModel(application) {

    enum class RecordingState {
        IDLE,
        RECORDING,
        RECORDED
    }

    private val repository = (application as DailerApplication).repository
    private val audioRecorder = AudioRecorder(application)
    private var timerJob: Job? = null

    private val _recordingState = MutableLiveData(RecordingState.IDLE)
    val recordingState: LiveData<RecordingState> = _recordingState

    private val _elapsedSeconds = MutableLiveData(0)
    val elapsedSeconds: LiveData<Int> = _elapsedSeconds

    private val _recordedFile = MutableLiveData<File?>(null)
    val recordedFile: LiveData<File?> = _recordedFile

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _enrollStatus = MutableLiveData<Result<String>?>(null)
    val enrollStatus: LiveData<Result<String>?> = _enrollStatus

    fun startRecording() {
        if (_recordingState.value == RecordingState.RECORDING) return

        runCatching {
            audioRecorder.startEnrollmentRecording("enroll_sample_${System.currentTimeMillis()}.m4a")
        }.onSuccess { file ->
            _recordedFile.value = file
            _elapsedSeconds.value = 0
            _recordingState.value = RecordingState.RECORDING

            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000L)
                    _elapsedSeconds.value = (_elapsedSeconds.value ?: 0) + 1
                }
            }
        }.onFailure { error ->
            _recordingState.value = RecordingState.IDLE
            _enrollStatus.value = Result.failure(error)
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        timerJob = null
        val file = audioRecorder.stop()
        _recordedFile.value = file
        _recordingState.value = if (file != null && file.exists()) {
            RecordingState.RECORDED
        } else {
            RecordingState.IDLE
        }
    }

    fun submitEnrollment(
        name: String,
        phoneNumber: String,
        relationship: String,
        inviteToken: String?
    ) {
        val sampleFile = _recordedFile.value
        if (name.isBlank() || phoneNumber.isBlank() || sampleFile == null || !sampleFile.exists()) {
            _enrollStatus.value = Result.failure(
                IllegalArgumentException("Missing enrollment details or voice sample")
            )
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val audioBase64 = AudioUtils.fileToBase64(sampleFile)
            if (audioBase64.isEmpty()) {
                Log.e(TAG, "Recorded enrollment audio file is empty: ${sampleFile.absolutePath}")
                _isLoading.value = false
                _enrollStatus.value = Result.failure(
                    IllegalStateException("Recorded audio is empty. Please record again.")
                )
                return@launch
            }
            val normalizedPhone = AudioUtils.normalizePhoneNumber(phoneNumber)
            Log.d(
                TAG,
                "Uploading enrollment: name=${name.trim()}, phone=$normalizedPhone, inviteToken=$inviteToken, audioBytes=${sampleFile.length()}"
            )
            val result = repository.enrollRelative(
                name = name.trim(),
                phoneNumber = normalizedPhone,
                relationship = relationship.trim().ifBlank { "Family" },
                audioBase64 = audioBase64,
                inviteToken = inviteToken
            )
            result.onSuccess { entity ->
                Log.i(TAG, "Enrollment succeeded: enrollmentId=${entity.enrollmentId}")
            }.onFailure { error ->
                Log.e(TAG, "Enrollment upload failed: ${error.message}", error)
            }
            _isLoading.value = false
            _enrollStatus.value = result.map { it.enrollmentId }
        }
    }

    companion object {
        private const val TAG = "EnrollViewModel"
    }

    fun clearEnrollStatus() {
        _enrollStatus.value = null
    }

    override fun onCleared() {
        timerJob?.cancel()
        if (audioRecorder.isRecording()) {
            audioRecorder.stop()
        }
        super.onCleared()
    }
}
