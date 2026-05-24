package com.applock.free

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.applock.free.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefManager: PrefManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }

        setupUI()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        // Refresh admin toggle state
        binding.switchUninstallProtection.isChecked = dpm.isAdminActive(adminComponent)
        updateRecoveryCodeDisplay()
    }

    private fun setupUI() {
        // Change PIN
        binding.btnChangePin.setOnClickListener { showChangePinDialog() }

        // Recovery code
        updateRecoveryCodeDisplay()
        binding.btnGenerateRecovery.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Generate Recovery Code")
                .setMessage("This will create a new 8-digit recovery code. Your old code will no longer work.\n\nGenerate new code?")
                .setPositiveButton("Generate") { _, _ ->
                    val code = prefManager.generateRecoveryCode()
                    showRecoveryCode(code)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Re-lock delay
        val delays = arrayOf("Immediately", "30 seconds", "1 minute", "5 minutes")
        val delayMs = arrayOf(0L, 30_000L, 60_000L, 300_000L)
        val currentDelay = prefManager.relockDelayMs
        val currentIndex = delayMs.indexOfFirst { it == currentDelay }.coerceAtLeast(0)
        binding.spinnerRelockDelay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, delays)
        binding.spinnerRelockDelay.setSelection(currentIndex)
        binding.spinnerRelockDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                prefManager.relockDelayMs = delayMs[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Uninstall protection
        binding.switchUninstallProtection.isChecked = dpm.isAdminActive(adminComponent)
        binding.switchUninstallProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Prevents App Lock from being uninstalled without your PIN.")
                }
                startActivityForResult(intent, REQUEST_ADMIN)
            } else {
                // Require PIN before disabling admin
                showPinConfirmBeforeAction("Disable uninstall protection?") {
                    dpm.removeActiveAdmin(adminComponent)
                    binding.switchUninstallProtection.isChecked = false
                    Toast.makeText(this, "Uninstall protection disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRecoveryCodeDisplay() {
        binding.tvRecoveryStatus.text = if (prefManager.recoveryCode.isNotEmpty())
            "Recovery code is set ✓" else "No recovery code set"
    }

    private fun showRecoveryCode(code: String) {
        AlertDialog.Builder(this)
            .setTitle("Your Recovery Code")
            .setMessage("Write this down and store it safely:\n\n" +
                "🔑  $code\n\n" +
                "This code can reset your PIN if you forget it. It will NOT be shown again.")
            .setPositiveButton("I've saved it", null)
            .setCancelable(false)
            .show()
        updateRecoveryCodeDisplay()
    }

    private fun showChangePinDialog() {
        showPinConfirmBeforeAction("Confirm current PIN to change it") {
            val view = layoutInflater.inflate(R.layout.dialog_pin_setup, null)
            val etNew     = view.findViewById<EditText>(R.id.etNewPin)
            val etConfirm = view.findViewById<EditText>(R.id.etConfirmPin)
            AlertDialog.Builder(this)
                .setTitle("Set New PIN")
                .setView(view)
                .setPositiveButton("Save") { _, _ ->
                    val newPin  = etNew.text.toString().trim()
                    val confirm = etConfirm.text.toString().trim()
                    when {
                        newPin.length < 4 -> Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                        newPin != confirm  -> Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                        else -> {
                            prefManager.pin = newPin
                            Toast.makeText(this, "PIN changed ✓", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showPinConfirmBeforeAction(message: String, onConfirmed: () -> Unit) {
        val input = EditText(this).apply {
            hint = "Enter current PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Confirm PIN")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (prefManager.checkPin(input.text.toString().trim())) {
                    onConfirmed()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN) {
            binding.switchUninstallProtection.isChecked = dpm.isAdminActive(adminComponent)
            if (dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Uninstall protection enabled ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_ADMIN = 100
    }
}
