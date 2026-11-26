package com.dailer.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dailerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.DATASTORE_NAME
)

val Context.setupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dailer_settings"
)

object Constants {
    // Networking
    const val BASE_URL = "http://10.0.2.2:5000/"

    // Notification Channels & IDs
    const val NOTIFICATION_CHANNEL_ID = "dailer_call_protection_channel"
    const val ALERT_NOTIFICATION_CHANNEL_ID = "dailer_voice_alerts_channel"
    const val RECORDING_NOTIFICATION_ID = 1001
    const val WARNING_NOTIFICATION_ID = 1002

    // Broadcast & Service Actions
    const val ACTION_INCOMING_CALL = "com.dailer.app.INCOMING_CALL"
    const val ACTION_VERIFICATION_RESULT = "com.dailer.app.VERIFICATION_RESULT"
    const val ACTION_START_RECORDING = "com.dailer.app.action.START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.dailer.app.action.STOP_RECORDING"

    // Intent / WorkData Extras
    const val EXTRA_PHONE_NUMBER = "phone_number"
    const val EXTRA_CALL_DIRECTION = "call_direction"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_RELATIVE_ID = "relative_id"
    const val EXTRA_AUDIO_PATH = "audio_path"
    const val EXTRA_CHUNK_INDEX = "chunk_index"
    const val EXTRA_VERDICT = "verdict"
    const val EXTRA_CONFIDENCE = "confidence"
    const val EXTRA_SYNTHETIC_SCORE = "synthetic_score"
    const val EXTRA_SPEAKER_SCORE = "speaker_score"
    const val EXTRA_REASON = "reason"

    // Call Directions
    const val CALL_DIRECTION_INCOMING = "INCOMING"
    const val CALL_DIRECTION_OUTGOING = "OUTGOING"

    // Verification Verdicts
    const val VERDICT_VERIFIED = "VERIFIED"
    const val VERDICT_SCAM_WARNING = "SCAM_WARNING"
    const val VERDICT_UNKNOWN = "UNKNOWN"
    const val VERDICT_ANALYZING = "ANALYZING"
    const val VERDICT_PENDING = "PENDING"

    // Deep Link Configuration
    const val DEEP_LINK_SCHEME = "dailer"
    const val DEEP_LINK_HOST = "enroll"
    const val DEEP_LINK_PREFIX = "dailer://enroll?token="

    // DataStore & EncryptedSharedPreferences Keys
    const val DATASTORE_NAME = "dailer_preferences"
    const val PREFS_FILE_NAME = "dailer_secure_prefs"
    const val PREF_BUFFER_DURATION_MS = "buffer_duration_ms"
    const val PREF_RETENTION_DAYS = "retention_days"
    const val PREF_AUTO_RECORD_ENABLED = "auto_record_enabled"
    const val PREF_BACKEND_URL = "backend_url"
    const val PREF_API_TOKEN = "api_token"

    // Audio & Recording Defaults
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_BIT_RATE = 64000
    const val DEFAULT_BUFFER_DURATION_MS = 120_000L
    const val CHUNK_DURATION_MS = 4000L

    // Retention & Upload Retry Defaults
    const val DEFAULT_RETENTION_DAYS = 30
    const val MAX_UPLOAD_RETRIES = 5
}
