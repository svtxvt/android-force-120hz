package com.forcehz.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit

/**
 * Restores the previously enabled 120Hz mode after reboot/app update.
 *
 * AccessibilityService startup is controlled by the system. We persist a
 * restore flag that the service consumes as soon as it is connected.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON -> restoreIfNeeded(context)
        }
    }

    private fun restoreIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(
            ForceHzAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(ForceHzAccessibilityService.PREF_ANIMATION_ENABLED, false)) {
            return
        }

        // Persist pending restore: service will apply it on next onServiceConnected.
        prefs.edit { putBoolean(ForceHzAccessibilityService.PREF_RESTORE_ON_CONNECT, true) }

        // Best effort: if service is already connected, restore immediately.
        if (isAccessibilityServiceEnabled(context)) {
            ForceHzAccessibilityService.instance?.startForceRefresh()
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val component = ComponentName(context, ForceHzAccessibilityService::class.java)
        val full = component.flattenToString()
        val short = component.flattenToShortString()

        return enabled.split(':').any { it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true) }
    }
}
