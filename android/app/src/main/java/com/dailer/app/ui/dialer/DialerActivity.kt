package com.dailer.app.ui.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dailer.app.R
import com.dailer.app.databinding.ActivityDialerBinding
import com.dailer.app.service.DailerInCallService

class DialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDialerBinding
    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (granted && !number.isNullOrBlank()) {
            placeCall(number)
        } else if (!granted) {
            Toast.makeText(this, R.string.status_permissions_pending, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowOnLockScreen()
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNumpad()
        handleDialIntent(intent)

        binding.btnCall.setOnClickListener {
            val number = binding.etPhoneNumber.text?.toString()?.trim().orEmpty()
            if (number.isEmpty()) {
                Toast.makeText(this, R.string.dialer_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            placeCall(number)
        }

        binding.btnEndCall.setOnClickListener {
            DailerInCallService.currentCall?.disconnect()
        }

        observeCallState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    private fun setupNumpad() {
        val digitButtons = listOf(
            binding.btnDigit1 to "1",
            binding.btnDigit2 to "2",
            binding.btnDigit3 to "3",
            binding.btnDigit4 to "4",
            binding.btnDigit5 to "5",
            binding.btnDigit6 to "6",
            binding.btnDigit7 to "7",
            binding.btnDigit8 to "8",
            binding.btnDigit9 to "9",
            binding.btnStar to "*",
            binding.btnDigit0 to "0",
            binding.btnHash to "#"
        )

        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { appendToNumber(digit) }
        }

        binding.btnDigit0.setOnLongClickListener {
            appendToNumber("+")
            true
        }

        binding.btnBackspace.setOnClickListener {
            val current = binding.etPhoneNumber.text?.toString().orEmpty()
            if (current.isNotEmpty()) {
                val updated = current.dropLast(1)
                binding.etPhoneNumber.setText(updated)
                binding.etPhoneNumber.setSelection(updated.length)
            }
        }

        binding.btnBackspace.setOnLongClickListener {
            binding.etPhoneNumber.text?.clear()
            true
        }
    }

    private fun appendToNumber(token: String) {
        val current = binding.etPhoneNumber.text?.toString().orEmpty()
        val updated = current + token
        binding.etPhoneNumber.setText(updated)
        binding.etPhoneNumber.setSelection(updated.length)
    }

    private fun handleDialIntent(intent: Intent?) {
        val number = intent?.data?.schemeSpecificPart
        if (!number.isNullOrBlank()) {
            binding.etPhoneNumber.setText(number)
            binding.etPhoneNumber.setSelection(number.length)
        }
    }

    private fun placeCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCallNumber = phoneNumber
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        val telecomManager = getSystemService(TelecomManager::class.java)
        val uri = Uri.fromParts("tel", phoneNumber, null)
        telecomManager?.placeCall(uri, Bundle())
        showInCallUi()
    }

    private fun observeCallState() {
        DailerInCallService.callState.observe(this) { callState ->
            val hasActiveOrRingingCall = DailerInCallService.currentCall != null &&
                callState.state != Call.STATE_DISCONNECTED

            if (hasActiveOrRingingCall) {
                showInCallUi()
                binding.btnEndCall.visibility = View.VISIBLE
            } else {
                hideInCallUi()
                binding.btnEndCall.visibility = View.GONE
            }
        }
    }

    private fun showInCallUi() {
        binding.inCallContainer.visibility = View.VISIBLE
        if (supportFragmentManager.findFragmentById(R.id.inCallContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.inCallContainer, InCallFragment())
                .commit()
        }
    }

    private fun hideInCallUi() {
        binding.inCallContainer.visibility = View.GONE
        val existing = supportFragmentManager.findFragmentById(R.id.inCallContainer)
        if (existing != null) {
            supportFragmentManager.beginTransaction()
                .remove(existing)
                .commit()
        }
    }

    private fun turnScreenOnAndShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
