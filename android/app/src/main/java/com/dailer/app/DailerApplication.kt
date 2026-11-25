package com.dailer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.os.Build
import com.dailer.app.data.local.DailerDatabase
import com.dailer.app.data.remote.RetrofitClient
import com.dailer.app.data.repository.DailerRepository
import com.dailer.app.util.Constants

class DailerApplication : Application() {

    val database: DailerDatabase by lazy { DailerDatabase.getInstance(this) }

    val repository: DailerRepository by lazy {
        DailerRepository(
            relativeDao = database.relativeDao(),
            callRecordDao = database.callRecordDao(),
            apiService = RetrofitClient.apiService
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize Room database singleton and notification channels on app startup
        database
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        val recordingChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_recording_text)
            setShowBadge(false)
        }

        val verdictChannel = NotificationChannel(
            CHANNEL_VERDICT,
            getString(R.string.notification_verdict_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_verdict_channel_desc)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_alert_channel_desc)
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
        }

        manager.createNotificationChannels(
            listOf(recordingChannel, verdictChannel, alertChannel)
        )
    }

    companion object {
        const val CHANNEL_RECORDING = Constants.NOTIFICATION_CHANNEL_ID
        const val CHANNEL_VERDICT = "dailer_verdict_channel"
        const val CHANNEL_ALERT = "dailer_alert_channel"
    }
}
