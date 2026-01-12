package com.forcehz.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for boot completed event.
 * Note: Accessibility Services are automatically started by the system if enabled,
 * so manual starting is generally not required.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Logic to run on boot if needed
        }
    }
}
