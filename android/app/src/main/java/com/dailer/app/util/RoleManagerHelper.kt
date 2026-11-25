package com.dailer.app.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.result.ActivityResultLauncher

object RoleManagerHelper {

    const val REQUEST_CODE_DEFAULT_DIALER = 1001

    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        return telecomManager?.defaultDialerPackage == context.packageName
    }

    @Suppress("DEPRECATION")
    fun requestDefaultDialerRole(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_DEFAULT_DIALER
    ) {
        if (isDefaultDialer(activity)) return
        val intent = createDefaultDialerIntent(activity) ?: return
        activity.startActivityForResult(intent, requestCode)
    }

    fun requestDefaultDialerRole(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ) {
        if (isDefaultDialer(activity)) return
        val intent = createDefaultDialerIntent(activity) ?: return
        launcher.launch(intent)
    }

    fun createDefaultDialerIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            }
        }
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }
}
