# App Lock — Ad-Free, Open Source and No Trackers

A completely ad-free Android app locker. No tracking, no ads, no internet permission.

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
