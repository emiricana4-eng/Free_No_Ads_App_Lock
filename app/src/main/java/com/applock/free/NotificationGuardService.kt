package com.applock.free

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationGuardService : NotificationListenerService() {

    private val prefManager by lazy {
        PrefManager(this)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {

        // Only react if user clicked notification
        if (reason != REASON_CLICK) {
            return
        }

        if (!prefManager.isEnabled) {
            return
        }

        val pkg = sbn.packageName

        // Ignore apps not locked
        if (!prefManager.isLocked(pkg)) {
            return
        }

        // Already unlocked this session
        if (pkg in LockService.unlockedApps) {
            return
        }

        // Polling recently paused after unlock
        if (
            System.currentTimeMillis() <
            LockService.pollPausedUntil
        ) {
            return
        }

        // Launch protection service
        LockService.start(this)
    }
}
