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
        // REASON_CLICK = 1 — user tapped the notification
        if (reason == REASON_CLICK && prefManager.isEnabled) {
            val pkg = sbn.packageName
            if (prefManager.isLocked(pkg) && pkg !in LockService.tempUnlocked) {
                // Notification tap will open a locked app — show lock overlay immediately
                LockService.pendingLockPackage = pkg
                LockService.start(this)
            }
        }
    }
}
