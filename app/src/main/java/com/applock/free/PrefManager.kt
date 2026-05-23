package com.applock.free

import android.content.Context

class PrefManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PIN, value).apply() }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    // Always returns a fresh mutable copy to avoid Android SharedPreferences mutation bug
    var lockedApps: MutableSet<String>
        get() = HashSet(prefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet())
        set(value) { prefs.edit().putStringSet(KEY_LOCKED_APPS, value).apply() }

    fun hasPin() = pin.isNotEmpty()

    fun isLocked(packageName: String) = lockedApps.contains(packageName)

    fun toggleApp(packageName: String) {
        val apps = lockedApps
        if (packageName in apps) apps.remove(packageName) else apps.add(packageName)
        lockedApps = apps
    }

    companion object {
        private const val PREFS_NAME = "applock_prefs"
        private const val KEY_PIN    = "pin"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LOCKED_APPS = "locked_apps"
    }
}
