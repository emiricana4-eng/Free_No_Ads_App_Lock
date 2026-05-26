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
        if (reason != REASON_CLICK) return
        if (!prefManager.isEnabled) return

        val pkg = sbn.packageName

        if (!prefManager.isLocked(pkg)) return

        val now = System.currentTimeMillis()
        if (pkg == LockService.lastAuthenticatedPackage && (now - LockService.authTimestamp) < 2000L) {
            return
        }

        if (pkg in LockService.unlockedApps) return

        if (now < LockService.pollPausedUntil) return

        LockService.start(this)
    }
}
