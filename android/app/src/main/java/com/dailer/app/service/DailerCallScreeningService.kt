package com.dailer.app.service

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants

class DailerCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val phoneNumber = if (rawNumber.isNotBlank()) {
            AudioUtils.normalizePhoneNumber(rawNumber)
        } else {
            ""
        }

        val directionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (callDetails.callDirection) {
                Call.Details.DIRECTION_INCOMING -> Constants.CALL_DIRECTION_INCOMING
                Call.Details.DIRECTION_OUTGOING -> Constants.CALL_DIRECTION_OUTGOING
                else -> "UNKNOWN"
            }
        } else {
            Constants.CALL_DIRECTION_INCOMING
        }

        Log.i(
            TAG,
            "Screening call: direction=$directionLabel, number=${if (phoneNumber.isBlank()) "<private>" else phoneNumber}"
        )

        val broadcastIntent = Intent(Constants.ACTION_INCOMING_CALL).apply {
            setPackage(packageName)
            putExtra(Constants.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(Constants.EXTRA_CALL_DIRECTION, directionLabel)
        }
        sendBroadcast(broadcastIntent)

        // Never block calls; voice verification happens during the call
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }

    companion object {
        private const val TAG = "DailerCallScreening"
    }
}
