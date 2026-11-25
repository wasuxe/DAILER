package com.dailer.app.util

import android.media.MediaMetadataRetriever
import android.util.Base64
import java.io.File

object AudioUtils {

    fun fileToBase64(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun bytesToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun getAudioDurationMs(file: File): Long {
        if (!file.exists() || !file.isFile || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun normalizePhoneNumber(rawNumber: String): String {
        val cleaned = rawNumber.trim().replace(Regex("[^+0-9]"), "")
        if (cleaned.isEmpty()) return ""
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }
}
