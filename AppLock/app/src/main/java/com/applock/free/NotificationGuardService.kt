package com.applock.free

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationGuardService : NotificationListenerService() {

    private val prefManager by lazy { PrefManager(this) }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        if (reason != REASON_CLICK || !prefManager.isEnabled) return

        val pkg = sbn.packageName
        if (!prefManager.isLocked(pkg)) return

        // --- NEW BYPASS LOGIC ---
        // Check if this notification click is for an app we JUST authenticated
        val now = System.currentTimeMillis()
        if (pkg == LockService.lastAuthenticatedPackage && (now - LockService.authTimestamp) < 2000L) {
            return
        }
        // ------------------------

        // Already unlocked this session
        if (pkg in LockService.unlockedApps) return

        // Polling recently paused after unlock
        if (now < LockService.pollPausedUntil) return

        // Launch protection service
        LockService.start(this)
    }
}
