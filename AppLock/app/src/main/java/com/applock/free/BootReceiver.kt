package com.applock.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PrefManager(context)
            if (prefs.isEnabled && prefs.hasPin()) {
                // Ensure the service is started as a Foreground Service
                LockService.start(context)
            }
        }
    }
}
