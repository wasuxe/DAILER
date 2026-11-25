package com.dailer.app.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dailer.app.util.Constants
import java.io.File

/**
 * Wrapper around [MediaRecorder] for call and voice-enrollment audio capture.
 *
 * VOICE_CALL audio source limitations on Android 10+ (API 29+):
 * Google restricted [MediaRecorder.AudioSource.VOICE_CALL] so that only system/privileged apps
 * holding `android.permission.CAPTURE_AUDIO_OUTPUT` can capture the downlink/uplink call stream
 * directly. Regular Play Store apps—including apps holding `ROLE_DIALER`—will fail in
 * `prepare()` or `start()` when requesting `VOICE_CALL` on most Android 10+ devices.
 * To handle this gracefully across devices and OEMs, [start] attempts `VOICE_CALL` first and
 * automatically falls back to [MediaRecorder.AudioSource.MIC].
 */
class AudioRecorder(
    private val context: Context,
    private var listener: Listener? = null
) {

    interface Listener {
        fun onRecordingComplete(file: File)
        fun onRecordingError(error: Throwable)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var autoStopRunnable: Runnable? = null

    @Volatile
    private var recording: Boolean = false

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start(
        outputFile: File,
        maxDurationMs: Long = Constants.DEFAULT_BUFFER_DURATION_MS
    ): Boolean {
        synchronized(lock) {
            stopInternal(deleteIfEmpty = true)

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.MIC
            )

            var lastError: Throwable? = null
            for (source in sources) {
                try {
                    val mediaRecorder = createAndStartMediaRecorder(source, outputFile)
                    recorder = mediaRecorder
                    currentOutputFile = outputFile
                    recording = true
                    scheduleAutoStop(maxDurationMs)
                    Log.i(TAG, "Started recording with audioSource=$source to ${outputFile.name}")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start MediaRecorder with audioSource=$source: ${e.message}")
                    lastError = e
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                }
            }

            recording = false
            currentOutputFile = null
            val error = lastError ?: IllegalStateException("Unable to start audio recording")
            listener?.onRecordingError(error)
            return false
        }
    }

    fun startEnrollmentRecording(fileName: String): File {
        synchronized(lock) {
            stopInternal(deleteIfEmpty = true)

            val outputFile = File(context.cacheDir, fileName)
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val mediaRecorder = createAndStartMediaRecorder(
                MediaRecorder.AudioSource.MIC,
                outputFile
            )
            recorder = mediaRecorder
            currentOutputFile = outputFile
            recording = true
            return outputFile
        }
    }

    fun stop(): File? {
        synchronized(lock) {
            return stopInternal(deleteIfEmpty = false)
        }
    }

    fun isRecording(): Boolean = recording

    private fun createAndStartMediaRecorder(audioSource: Int, outputFile: File): MediaRecorder {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mediaRecorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(Constants.AUDIO_SAMPLE_RATE)
                setAudioEncodingBitRate(Constants.AUDIO_BIT_RATE)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder
        } catch (e: Exception) {
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
            throw e
        }
    }

    private fun scheduleAutoStop(maxDurationMs: Long) {
        cancelAutoStop()
        if (maxDurationMs <= 0L) return

        val runnable = Runnable {
            val completedFile = stop()
            if (completedFile != null && completedFile.exists() && completedFile.length() > 0L) {
                listener?.onRecordingComplete(completedFile)
            }
        }
        autoStopRunnable = runnable
        handler.postDelayed(runnable, maxDurationMs)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
    }

    private fun stopInternal(deleteIfEmpty: Boolean): File? {
        cancelAutoStop()
        val file = currentOutputFile
        currentOutputFile = null

        val activeRecorder = recorder
        recorder = null
        val wasRecording = recording
        recording = false

        if (activeRecorder == null) {
            return null
        }

        var stoppedCleanly = false
        try {
            if (wasRecording) {
                activeRecorder.stop()
                stoppedCleanly = true
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "MediaRecorder.stop() failed (likely too short): ${e.message}")
            file?.delete()
            return null
        } finally {
            runCatching { activeRecorder.release() }
        }

        if (!stoppedCleanly || file == null || !file.exists()) {
            return null
        }
        if (deleteIfEmpty && file.length() == 0L) {
            file.delete()
            return null
        }
        return file
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
