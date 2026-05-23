# App Lock — Ad-Free, Open Source

A minimal, completely ad-free app locker for Android.
No tracking, no accounts, no internet permission.

---

## Features
- Lock any user-installed app behind a PIN
- Auto-relocks when you leave and return to the app
- Survives reboots (auto-restarts service)
- Foreground service with minimal notification
- Fingerprint support can be added (see notes below)

---

## Build Instructions

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK with API 34

### Steps

1. Open Android Studio → File → New → Import Project
2. Select this folder (AppLock/)
3. Wait for Gradle sync to complete
4. Connect your phone or start an emulator
5. Run → Run 'app'

If Gradle sync fails, try: File → Invalidate Caches / Restart

---

## First-Time Setup on Phone

1. Open App Lock
2. Tap **Set PIN** and choose a 4–8 digit PIN
3. Tap the **⚠ banner** to grant Usage Access permission
   - Find "App Lock" in the list → enable it
4. Toggle **Active** to ON
5. Go to the app list and toggle the apps you want locked

---

## How It Works

- A foreground service polls `UsageStatsManager` every 500ms
- When a locked app comes to the foreground, `LockActivity` is launched on top
- After correct PIN entry, the app is temporarily unlocked for that session
- When the user leaves and returns to the app, it re-locks

---

## Permissions Used

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is in the foreground |
| `FOREGROUND_SERVICE` | Keep monitoring service running |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `POST_NOTIFICATIONS` | Show the "App Lock Active" notification |

No internet permission. No analytics. No ads. Ever.

---

## Optional: Add Fingerprint Support

In `LockActivity.kt`, add a `BiometricPrompt` call after `setupNumpad()`:

```kotlin
val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        grantAccess()
    }
})
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Unlock ${binding.tvAppName.text}")
    .setNegativeButtonText("Use PIN")
    .build()
biometricPrompt.authenticate(promptInfo)
```

Add dependency in app/build.gradle.kts:
```kotlin
implementation("androidx.biometric:biometric:1.1.0")
```
