package com.applock.free

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class LockService : Service() {

    private lateinit var prefManager: PrefManager
    private val handler = Handler(Looper.getMainLooper())

    // Track last seen foreground app to detect app switches
    private var lastForeground = ""

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (prefManager.isEnabled) {
                checkForeground()
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: OS will restart the service if it gets killed
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Core detection logic
    // -------------------------------------------------------------------------

    private fun checkForeground() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5_000, now)
        if (stats.isNullOrEmpty()) return

        val topApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return

        // Never lock our own app
        if (topApp == packageName) return

        // App switched: revoke temporary unlock for the app the user just left
        if (topApp != lastForeground) {
            tempUnlocked.remove(lastForeground)
            lastForeground = topApp
        }

        // Show lock screen if app is locked and not currently unlocked this session
        if (prefManager.isLocked(topApp) && topApp !in tempUnlocked) {
            launchLockScreen(topApp)
        }
    }

    private fun launchLockScreen(packageName: String) {
        val intent = Intent(this, LockActivity::class.java).apply {
            putExtra(LockActivity.EXTRA_PACKAGE, packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Notification (required for foreground service)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps your selected apps locked"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Lock")
            .setContentText("Your apps are protected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // Companion — static state shared with LockActivity
    // -------------------------------------------------------------------------

    companion object {
        private const val NOTIF_ID   = 1001
        private const val CHANNEL_ID = "applock_channel"
        private const val POLL_MS    = 500L

        /** Apps the user has successfully unlocked this session. Cleared when they leave. */
        val tempUnlocked = mutableSetOf<String>()

        fun start(context: Context) {
            val intent = Intent(context, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockService::class.java))
        }
    }
}
