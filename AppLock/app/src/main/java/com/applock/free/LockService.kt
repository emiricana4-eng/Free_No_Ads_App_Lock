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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) handler.post { checkForeground() }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            // Only poll if not paused (pause happens right after PIN entry)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        schedule(1)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        lockOverlay.hide()
        schedule(2)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedule(id: Int) {
        val restart = PendingIntent.getService(
            applicationContext, id,
            Intent(applicationContext, LockService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, restart)
    }

    private fun checkForeground() {
        if (!Settings.canDrawOverlays(this)) return

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5_000, now)
        if (stats.isNullOrEmpty()) return

        val topApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return

        if (topApp == packageName) {
            lockOverlay.hide()
            return
        }

        // Record when the previous app was left
        if (topApp != lastTopApp && lastTopApp.isNotEmpty() && lastTopApp != packageName) {
            appLeftAt[lastTopApp] = now
        }
        lastTopApp = topApp

        // Not a locked app — hide overlay if showing
        if (!prefManager.isLocked(topApp)) {
            if (lockOverlay.isShowing() && lockOverlay.currentPackage == topApp) lockOverlay.hide()
            return
        }

        // App is locked — is it currently in unlocked state?
        if (unlockedApps.contains(topApp)) {
            val leftAt = appLeftAt[topApp]
            if (leftAt == null) {
                // User hasn't left the app since unlocking — keep unlocked
                if (lockOverlay.isShowing()) lockOverlay.hide()
                return
            }
            val relockDelay = prefManager.relockDelayMs
            val timeSinceLeft = now - leftAt
            if (timeSinceLeft <= relockDelay) {
                // Still within grace period
                if (lockOverlay.isShowing()) lockOverlay.hide()
                return
            }
            // Grace period over — re-lock
            unlockedApps.remove(topApp)
            appLeftAt.remove(topApp)
        }

        lockOverlay.show(topApp)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App Lock", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps your selected apps locked"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
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
        private const val NOTIF_ID   = 1001
        private const val CHANNEL_ID = "applock_channel"
        private const val POLL_MS    = 300L

        // Polling is paused for 2s after PIN entry so startActivity transition doesn't re-trigger lock
        var pollPausedUntil = 0L

        // Apps the user has successfully unlocked this session
        val unlockedApps = mutableSetOf<String>()

        // When each app was last left (to calculate relock delay)
        val appLeftAt = mutableMapOf<String, Long>()

        // Last detected foreground app
        var lastTopApp = ""

        fun start(context: Context) {
            val intent = Intent(context, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) = context.stopService(Intent(context, LockService::class.java))
    }
}
