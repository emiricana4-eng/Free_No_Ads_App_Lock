# App Lock — Ad-Free, Open Source

A completely ad-free Android app locker. No tracking, no ads, no internet permission.

---

## ⚡ Get the APK via GitHub Actions (No installs needed)

1. Go to **github.com** → sign in (or create free account)
2. Click **+** → **New repository** → name it `AppLock` → **Create**
3. Upload this entire folder (drag & drop all files onto the repo page)
4. GitHub auto-triggers the build — wait ~3 minutes
5. Click **Actions** tab → click the latest run → scroll to **Artifacts**
6. Download **AppLock-debug** → unzip → install the `.apk` on your phone

That's it. No Android Studio, no local setup.

---

## Features
- Lock any user-installed app behind a PIN
- Re-locks every time you leave and return
- Survives reboots
- Zero internet permission — cannot phone home
- No ads, no SDKs, no trackers

---

## First-Time Setup on Phone

1. Open App Lock
2. Tap **Set PIN** → choose a 4–8 digit PIN
3. Tap the red ⚠ banner → grant **Usage Access** for App Lock
4. Toggle **Active** → ON
5. Flip the switch next to any app to lock it

---

## Permissions

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is in the foreground |
| `FOREGROUND_SERVICE` | Keep the monitoring service running |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `POST_NOTIFICATIONS` | Show the persistent "protected" notification |

No `INTERNET` permission. Ever.

---

## Build Locally (Android Studio)

1. Install Android Studio from developer.android.com/studio
2. Open this folder → let Gradle sync
3. Plug in phone → Run ▶
