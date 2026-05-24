package com.applock.free

import android.content.Context

class PrefManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PIN, value).apply() }

    fun checkPin(input: String) = input == pin

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var lockedApps: MutableSet<String>
        get() = HashSet(prefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet())
        set(value) { prefs.edit().putStringSet(KEY_LOCKED_APPS, value).apply() }

    var relockDelayMs: Long
        get() = prefs.getLong(KEY_RELOCK_DELAY, 0L)
        set(value) { prefs.edit().putLong(KEY_RELOCK_DELAY, value).apply() }

    var recoveryCode: String
        get() = prefs.getString(KEY_RECOVERY_CODE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_RECOVERY_CODE, value).apply() }

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT, false)
        set(value) { prefs.edit().putBoolean(KEY_BATTERY_PROMPT, value).apply() }

    fun hasPin() = pin.isNotEmpty()
    fun isLocked(pkg: String) = lockedApps.contains(pkg)

    fun toggleApp(pkg: String) {
        val apps = lockedApps
        if (pkg in apps) apps.remove(pkg) else apps.add(pkg)
        lockedApps = apps
    }

    fun generateRecoveryCode(): String {
        val code = (10000000..99999999).random().toString()
        recoveryCode = code
        return code
    }

    fun checkRecoveryCode(input: String) = recoveryCode.isNotEmpty() && input == recoveryCode

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }

    companion object {
        private const val PREFS_NAME         = "applock_prefs"
        private const val KEY_PIN            = "pin"
        private const val KEY_ENABLED        = "enabled"
        private const val KEY_LOCKED_APPS    = "locked_apps"
        private const val KEY_RELOCK_DELAY   = "relock_delay"
        private const val KEY_RECOVERY_CODE  = "recovery_code"
        private const val KEY_BATTERY_PROMPT = "battery_prompt"
    }
}
