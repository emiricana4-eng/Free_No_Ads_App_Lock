package com.applock.free

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat

class LockService : Service() {

    private lateinit var prefManager: PrefManager
    private lateinit var lockOverlay: LockOverlay
    private val handler = Handler(Looper.getMainLooper())

    private val ignoredPackages by lazy {
        setOf(
            packageName,
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.launcher",
            "com.google.android.launcher",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.android.inputmethod.latin"
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                handler.post { checkForeground() }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (prefManager.isEnabled && System.currentTimeMillis() > pollPausedUntil) {
                checkForeground()
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        lockOverlay = LockOverlay(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        WatchdogJobService.schedule(this)
        handler.post(pollRunnable)
    }

    private fun checkForeground() {
        if (!Settings.canDrawOverlays(this)) return

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000, now)

        if (stats.isNullOrEmpty()) return

        val topApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return

        // --- NEW BYPASS LOGIC ---
        // Prevents the "instant relock" loop immediately after PIN entry
        if (topApp == lastAuthenticatedPackage && (now - authTimestamp) < 2000L) {
            return
        }
        // ------------------------

        if (topApp in ignoredPackages) return

        if (topApp != lastTopApp) {
            if (now - lastForegroundChange < FOREGROUND_DEBOUNCE_MS) return
            lastForegroundChange = now
            if (lastTopApp.isNotEmpty() && lastTopApp !in ignoredPackages) {
                appLeftAt[lastTopApp] = now
            }
            lastTopApp = topApp
        }

        if (!prefManager.isLocked(topApp)) {
            if (lockOverlay.isShowing() && lockOverlay.currentPackage == topApp) {
                lockOverlay.hide()
            }
            return
        }

        if (unlockedApps.contains(topApp) || tempUnlocked.contains(topApp)) {
            val leftAt = appLeftAt[topApp]
            if (leftAt == null) {
                lockOverlay.hide()
                return
            }
            val relockDelay = prefManager.relockDelayMs
            val timeAway = now - leftAt
            if (timeAway <= relockDelay) {
                lockOverlay.hide()
                return
            }
            unlockedApps.remove(topApp)
            tempUnlocked.remove(topApp)
            appLeftAt.remove(topApp)
        }

        pendingLockPackage = topApp
        if (!lockOverlay.isShowing() || lockOverlay.currentPackage != topApp) {
            lockOverlay.show(topApp)
        }
    }

    // [Keep your existing onDestroy, onStartCommand, onBind, and notification methods here]
    // ...

    companion object {
        // ... Existing constants ...
        private const val POLL_MS = 2000L // Increased for better system stability
        private const val FOREGROUND_DEBOUNCE_MS = 1200L

        @JvmField var pollPausedUntil = 0L
        @JvmField val unlockedApps = mutableSetOf<String>()
        @JvmField val tempUnlocked = mutableSetOf<String>()
        @JvmField var pendingLockPackage: String? = null
        @JvmField val appLeftAt = mutableMapOf<String, Long>()
        @JvmField var lastTopApp = ""
        @JvmField var lastForegroundChange = 0L

        // Added variables for the fix
        @JvmField var lastAuthenticatedPackage: String? = null
        @JvmField var authTimestamp: Long = 0L
        
        // ... (Keep your start/stop methods)
    }
}
