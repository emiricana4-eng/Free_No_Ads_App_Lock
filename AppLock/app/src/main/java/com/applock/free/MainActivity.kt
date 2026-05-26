package com.applock.free

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.applock.free.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PrefManager
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefManager = PrefManager(this)
        setupRecyclerView()
        setupControls()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        refreshBanners()
        syncToggle()
        refreshPinCard()
        if (!prefManager.batteryPromptShown) promptBatteryOptimization()
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(prefManager) { prefManager.toggleApp(it) }
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupControls() {
        binding.btnSetPin.setOnClickListener { showSetPinDialog() }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tvUsageBanner.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        
        binding.tvOverlayBanner.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            when {
                isChecked && !hasUsagePermission() -> {
                    binding.switchEnable.isChecked = false
                    showPermissionDialog("Usage Access",
                        "App Lock needs 'Usage Access' to detect which app is open.\n\nFind 'App Lock' in the list and enable it.",
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                isChecked && !hasOverlayPermission() -> {
                    binding.switchEnable.isChecked = false
                    showPermissionDialog("Display Over Other Apps",
                        "App Lock needs permission to show the lock screen on top of other apps.",
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
                        WatchdogJobService.schedule(this) 
                    } else {
                        LockService.stop(this)
                    }
                    Toast.makeText(this, if (isChecked) "App Lock enabled ✓" else "App Lock disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshPinCard() {
        if (prefManager.hasPin()) {
            binding.pinSetupCard.visibility = View.GONE
            binding.btnSettings.visibility = View.VISIBLE
        } else {
            binding.pinSetupCard.visibility = View.VISIBLE
            binding.btnSettings.visibility = View.GONE
        }
    }

    private fun syncToggle() {
        binding.switchEnable.setOnCheckedChangeListener(null)
        binding.switchEnable.isChecked = prefManager.isEnabled
        setupControls()
        
        if (prefManager.isEnabled && hasUsagePermission() && hasOverlayPermission()) {
            LockService.start(this)
            WatchdogJobService.schedule(this)
        }
    }

    private fun refreshBanners() {
        binding.tvUsageBanner.visibility   = if (hasUsagePermission())   View.GONE else View.VISIBLE
        binding.tvOverlayBanner.visibility = if (hasOverlayPermission()) View.GONE else View.VISIBLE
    }

    private fun loadInstalledApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE
        Thread {
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .filter { it != packageName }
                .toSet()
                .mapNotNull { pkg ->
                    try { AppInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pm.getApplicationIcon(pkg)) }
                    catch (_: Exception) { null }
                }
                .sortedBy { it.name.lowercase() }
            runOnUiThread {
                adapter.setApps(apps)
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun showSetPinDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_pin_setup, null)
        val etNew     = view.findViewById<EditText>(R.id.etNewPin)
        val etConfirm = view.findViewById<EditText>(R.id.etConfirmPin)
        
        AlertDialog.Builder(this)
            .setTitle("Set PIN")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newPin  = etNew.text.toString().trim()
                val confirm = etConfirm.text.toString().trim()
                when {
                    newPin.length < 4 -> Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    newPin != confirm  -> Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                    else -> {
                        prefManager.pin = newPin
                        Toast.makeText(this, "PIN saved ✓ — Generate a recovery code in Settings!", Toast.LENGTH_LONG).show()
                        refreshPinCard()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun promptBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        
        prefManager.batteryPromptShown = true
        AlertDialog.Builder(this)
            .setTitle("⚠ Improve Reliability")
            .setMessage("Samsung's battery optimizer may stop App Lock from working in the background.\n\nTap OK → find 'App Lock' → set to 'Unrestricted'.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton("Later", null).show()
    }

    private fun hasUsagePermission(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun showPermissionDialog(title: String, message: String, intent: Intent) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Open Settings") { _, _ -> startActivity(intent) }
            .setNegativeButton("Cancel", null).show()
    }
}
