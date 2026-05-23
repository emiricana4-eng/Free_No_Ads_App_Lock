package com.applock.free

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.applock.free.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PrefManager
    private lateinit var adapter: AppListAdapter

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

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
        refreshPermissionBanner()
        syncToggleState()
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupRecyclerView() {
        adapter = AppListAdapter(prefManager) { pkg ->
            prefManager.toggleApp(pkg)
        }
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupControls() {
        binding.btnSetPin.setOnClickListener { showPinDialog() }

        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            when {
                isChecked && !hasUsagePermission() -> {
                    binding.switchEnable.isChecked = false
                    showPermissionDialog()
                }
                isChecked && !prefManager.hasPin() -> {
                    binding.switchEnable.isChecked = false
                    Toast.makeText(this, "Please set a PIN first", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    prefManager.isEnabled = isChecked
                    if (isChecked) LockService.start(this) else LockService.stop(this)
                    val msg = if (isChecked) "App Lock enabled" else "App Lock disabled"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.tvPermissionBanner.setOnClickListener { showPermissionDialog() }
    }

    private fun syncToggleState() {
        // Silently sync without firing the listener
        binding.switchEnable.setOnCheckedChangeListener(null)
        binding.switchEnable.isChecked = prefManager.isEnabled
        setupControls() // Re-attach listener

        if (prefManager.isEnabled && hasUsagePermission()) {
            LockService.start(this)
        }
    }

    private fun refreshPermissionBanner() {
        binding.tvPermissionBanner.visibility =
            if (hasUsagePermission()) View.GONE else View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // App loading
    // -------------------------------------------------------------------------

    private fun loadInstalledApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE

        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { isUserApp(it) }
                .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it.packageName)) }
                .sortedBy { it.name.lowercase() }

            runOnUiThread {
                adapter.setApps(apps)
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun isUserApp(info: ApplicationInfo): Boolean {
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return !isSystem && info.packageName != packageName
    }

    // -------------------------------------------------------------------------
    // PIN dialog
    // -------------------------------------------------------------------------

    private fun showPinDialog() {
        val existingPin = prefManager.pin
        val title = if (existingPin.isEmpty()) "Set PIN" else "Change PIN"

        val container = layoutInflater.inflate(R.layout.dialog_pin_setup, null)
        val etNew     = container.findViewById<EditText>(R.id.etNewPin)
        val etConfirm = container.findViewById<EditText>(R.id.etConfirmPin)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newPin     = etNew.text.toString().trim()
                val confirmPin = etConfirm.text.toString().trim()
                when {
                    newPin.length < 4 ->
                        Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    newPin != confirmPin ->
                        Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                    else -> {
                        prefManager.pin = newPin
                        Toast.makeText(this, "PIN saved ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Permission helpers
    // -------------------------------------------------------------------------

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode   = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access Required")
            .setMessage(
                "App Lock needs 'Usage Access' permission to detect which app is open.\n\n" +
                "In the next screen, find \"App Lock\" in the list and enable it."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
