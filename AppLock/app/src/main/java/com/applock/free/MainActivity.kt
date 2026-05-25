// ... (keep imports)

    private fun setupControls() {
        binding.btnSetPin.setOnClickListener { showSetPinDialog() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            when {
                isChecked && !hasUsagePermission() -> {
                    binding.switchEnable.isChecked = false
                    showPermissionDialog("Usage Access", 
                        "App Lock needs 'Usage Access' to detect which app is open.", 
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                isChecked && !hasOverlayPermission() -> {
                    binding.switchEnable.isChecked = false
                    showPermissionDialog("Display Over Other Apps", 
                        "App Lock needs permission to show the lock screen.", 
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                isChecked && !prefManager.hasPin() -> {
                    binding.switchEnable.isChecked = false
                    Toast.makeText(this, "Please set a PIN first", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    prefManager.isEnabled = isChecked
                    if (isChecked) {
                        LockService.start(this)
                        WatchdogJobService.schedule(this) // Ensure watchdog is active
                    } else {
                        LockService.stop(this)
                    }
                    Toast.makeText(this, if (isChecked) "App Lock enabled ✓" else "App Lock disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun syncToggle() {
        binding.switchEnable.setOnCheckedChangeListener(null)
        binding.switchEnable.isChecked = prefManager.isEnabled
        // Re-attach listener after manual update
        setupControls() 
        
        // Ensure consistency between Service and Watchdog
        if (prefManager.isEnabled && hasUsagePermission() && hasOverlayPermission()) {
            LockService.start(this)
            WatchdogJobService.schedule(this)
        }
    }

// ... (keep the rest of the methods)
