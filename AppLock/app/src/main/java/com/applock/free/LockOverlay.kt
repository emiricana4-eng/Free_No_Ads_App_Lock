package com.applock.free

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class LockOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefManager = PrefManager(context)
    private var overlayView: android.view.View? = null
    private var enteredPin = ""
    private var wrongAttempts = 0
    private var recoveryMode = false

    var currentPackage = ""

    fun show(packageName: String) {
        currentPackage = packageName
        if (overlayView != null) { updateAppName(); return }
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_lock, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        setupButtons(view)
        windowManager.addView(view, params)
        overlayView = view
        updateAppName()
    }

    fun hide() {
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        enteredPin = ""
        wrongAttempts = 0
        recoveryMode = false
    }

    fun isShowing() = overlayView != null

    private fun updateAppName() {
        val tv = overlayView?.findViewById<TextView>(R.id.tvAppName) ?: return
        val label = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(currentPackage, 0)
            ).toString()
        } catch (_: Exception) { "App" }
        tv.text = "Unlock $label"
    }

    private fun setupButtons(view: android.view.View) {
        recoveryMode = false
        enteredPin = ""
        val tvDots   = view.findViewById<TextView>(R.id.tvPinDots)
        val tvName   = view.findViewById<TextView>(R.id.tvAppName)
        val tvForgot = view.findViewById<TextView>(R.id.tvForgotPin)
        updateDots(tvDots)

        listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
               R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        ).forEachIndexed { index, id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (enteredPin.length < 8) {
                    enteredPin += index.toString()
                    updateDots(tvDots)
                    val target = if (recoveryMode) 8 else prefManager.pin.length
                    if (target > 0 && enteredPin.length >= target) {
                        if (recoveryMode) checkRecovery(tvDots) else checkPin(tvDots)
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            if (enteredPin.isNotEmpty()) { enteredPin = enteredPin.dropLast(1); updateDots(tvDots) }
        }
        view.findViewById<Button>(R.id.btnClear).setOnClickListener {
            enteredPin = ""; updateDots(tvDots)
        }

        tvForgot.setOnClickListener {
            recoveryMode = !recoveryMode
            enteredPin = ""
            if (recoveryMode) {
                tvName.text = "Enter 8-digit recovery code"
                tvForgot.text = "← Back to PIN"
                tvDots.text = "○○○○○○○○"
            } else {
                updateAppName()
                tvForgot.text = "Forgot PIN?"
                updateDots(tvDots)
            }
        }
    }

    private fun updateDots(tvDots: TextView) {
        val total = if (recoveryMode) 8 else prefManager.pin.length.coerceAtLeast(4)
        tvDots.text = "●".repeat(enteredPin.length) +
                "○".repeat((total - enteredPin.length).coerceAtLeast(0))
    }

    private fun checkPin(tvDots: TextView) {
        if (prefManager.checkPin(enteredPin)) {
            grantAccess(currentPackage)
        } else {
            wrongAttempts++
            vibrate()
            enteredPin = ""
            updateDots(tvDots)
            toast(if (wrongAttempts >= 3) "Wrong PIN — $wrongAttempts attempts" else "Wrong PIN")
        }
    }

    private fun checkRecovery(tvDots: TextView) {
        if (prefManager.checkRecoveryCode(enteredPin)) {
            prefManager.clearPin()
            grantAccess(currentPackage)
            toast("Recovery successful! Please set a new PIN.")
        } else {
            vibrate()
            enteredPin = ""
            tvDots.text = "○○○○○○○○"
            toast("Wrong recovery code")
        }
    }

    private fun grantAccess(pkg: String) {
        // 1. Pause polling for 2 seconds — prevents any re-lock during app launch transition
        LockService.pollPausedUntil = System.currentTimeMillis() + 2000L

        // 2. Mark app as unlocked and clear any "left" timestamp
        LockService.unlockedApps.add(pkg)
        LockService.appLeftAt.remove(pkg)
        LockService.lastTopApp = pkg

        // 3. Dismiss overlay
        hide()

        // 4. Bring the app to foreground
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun vibrate() {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}
