package com.applock.free

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applock.free.databinding.ActivityLockBinding

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var prefManager: PrefManager

    private var packageToUnlock = ""
    private var enteredPin = ""

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)
        packageToUnlock = intent.getStringExtra(EXTRA_PACKAGE) ?: ""

        // Edge case: if no PIN is set, just let them in
        if (!prefManager.hasPin()) {
            grantAccess()
            return
        }

        showAppName()
        setupNumpad()
        updateDots()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Service may re-send intent for a different app while we're already showing
        val newPackage = intent?.getStringExtra(EXTRA_PACKAGE)
        if (!newPackage.isNullOrEmpty() && newPackage != packageToUnlock) {
            packageToUnlock = newPackage
            showAppName()
            clearPin()
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun showAppName() {
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageToUnlock, 0)
            ).toString()
        } catch (e: Exception) { "App" }
        binding.tvAppName.text = "Unlock $label"
    }

    private fun setupNumpad() {
        val digitButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )
        digitButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { appendDigit(index.toString()) }
        }
        binding.btnBackspace.setOnClickListener { removeLastDigit() }
        binding.btnClear.setOnClickListener { clearPin() }
    }

    // -------------------------------------------------------------------------
    // PIN logic
    // -------------------------------------------------------------------------

    private fun appendDigit(digit: String) {
        if (enteredPin.length >= MAX_PIN_LENGTH) return
        enteredPin += digit
        updateDots()
        // Auto-check once we reach the stored PIN length
        if (enteredPin.length >= prefManager.pinLength) {
    checkPin()
}

    private fun removeLastDigit() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateDots()
        }
    }

    private fun clearPin() {
        enteredPin = ""
        updateDots()
    }

    private fun updateDots() {
        val total = prefManager.pinLength.coerceAtLeast(4)
        val filled = enteredPin.length
        binding.tvPinDots.text =
            "●".repeat(filled) + "○".repeat((total - filled).coerceAtLeast(0))
    }

    private fun checkPin() {
    if (prefManager.checkPin(enteredPin)) {
        grantAccess()
    } else {
        onWrongPin()
    }
}

    private fun grantAccess() {

    LockService.unlockedApps.add(packageToUnlock)

    LockService.tempUnlocked.add(packageToUnlock)

    LockService.appLeftAt.remove(packageToUnlock)

    LockService.pollPausedUntil =
        System.currentTimeMillis() + 3000

    finish()
}

    private fun onWrongPin() {
        // Shake the dots
        val shake = AnimationUtils.loadAnimation(this, android.R.anim.cycle_interpolator)
        binding.tvPinDots.startAnimation(shake)
        // Vibrate
        @Suppress("DEPRECATION")
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
        clearPin()
    }

    // -------------------------------------------------------------------------
    // Block back/home from revealing the locked app
    // -------------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            goHome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        private const val MAX_PIN_LENGTH = 8
    }
}
