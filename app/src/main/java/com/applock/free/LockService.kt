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
            "com.android.systemui"
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

            if (
                prefManager.isEnabled &&
                System.currentTimeMillis() > pollPausedUntil
            ) {
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

        startForeground(
            NOTIF_ID,
            buildNotification()
        )

        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON)
        )

        WatchdogJobService.schedule(this)

        handler.post(pollRunnable)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        schedule(1)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {

        handler.removeCallbacks(pollRunnable)

        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }

        lockOverlay.hide()

        schedule(2)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedule(id: Int) {

        val restart = PendingIntent.getService(
            applicationContext,
            id,
            Intent(applicationContext, LockService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        (
            getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager
            )
            .set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restart
            )
    }

    private fun checkForeground() {

        if (!Settings.canDrawOverlays(this)) return

        val usm =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 5_000,
            now
        )

        if (stats.isNullOrEmpty()) return

        val topApp =
            stats.maxByOrNull {
                it.lastTimeUsed
            }?.packageName ?: return

        // Ignore our own app and Android transitions
        if (topApp in ignoredPackages) {
            return
        }

        // Track when user left previous app
        if (
            topApp != lastTopApp &&
            lastTopApp.isNotEmpty() &&
            lastTopApp !in ignoredPackages
        ) {
            appLeftAt[lastTopApp] = now
        }

        lastTopApp = topApp

        // App is not locked
        if (!prefManager.isLocked(topApp)) {

            if (
                lockOverlay.isShowing() &&
                lockOverlay.currentPackage == topApp
            ) {
                lockOverlay.hide()
            }

            return
        }

        // Already unlocked
        if (
            unlockedApps.contains(topApp) ||
            tempUnlocked.contains(topApp)
        ) {

            val leftAt = appLeftAt[topApp]

            // Never left app yet
            if (leftAt == null) {

                if (lockOverlay.isShowing()) {
                    lockOverlay.hide()
                }

                return
            }

            val relockDelay =
                prefManager.relockDelayMs

            val timeSinceLeft =
                now - leftAt

            // Still inside grace period
            if (timeSinceLeft <= relockDelay) {

                if (lockOverlay.isShowing()) {
                    lockOverlay.hide()
                }

                return
            }

            // Grace period expired
            unlockedApps.remove(topApp)
            tempUnlocked.remove(topApp)
            appLeftAt.remove(topApp)
        }

        pendingLockPackage = topApp

        lockOverlay.show(topApp)
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock",
                NotificationManager.IMPORTANCE_MIN
            ).apply {

                description =
                    "Keeps your selected apps locked"

                setShowBadge(false)
            }

            (
                getSystemService(NOTIFICATION_SERVICE)
                        as NotificationManager
                )
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("App Lock Active")
            .setContentText("Your apps are protected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {

        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "applock_channel"

        // Less aggressive polling
        private const val POLL_MS = 750L

        // Pause polling briefly after unlock
        @JvmField
        var pollPausedUntil = 0L

        // Apps unlocked during active session
        @JvmField
        val unlockedApps = mutableSetOf<String>()

        // Legacy compatibility references
        @JvmField
        val tempUnlocked = mutableSetOf<String>()

        @JvmField
        var pendingLockPackage: String? = null

        // Tracks when app was left
        @JvmField
        val appLeftAt =
            mutableMapOf<String, Long>()

        // Last foreground app
        @JvmField
        var lastTopApp = ""

        fun start(context: Context) {

            val intent = Intent(
                context,
                LockService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {

            context.stopService(
                Intent(
                    context,
                    LockService::class.java
                )
            )
        }
    }
}
