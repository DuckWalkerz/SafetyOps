package com.safetyops.field

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    private val stage1 = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )
    private val stage2 = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    private val stage3 = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root Background (Off-white / Slate)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#F8FAFC")) // Professional off-white
        }

        // Logo / Title
        val title = TextView(this).apply {
            text = "Judiciary Furlough Initiative"
            textSize = 22f
            setTextColor(Color.parseColor("#0F172A")) // Slate 900
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Official Field Monitoring"
            textSize = 14f
            setTextColor(Color.parseColor("#64748B")) // Slate 500
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 48)
        }

        // Status Card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
        }

        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444")) // Red (Offline)
            }
            layoutParams = LinearLayout.LayoutParams(20, 20).apply { marginEnd = 16 }
        }

        statusText = TextView(this).apply {
            text = "System Standby"
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        statusCard.addView(statusDot)
        statusCard.addView(statusText)

        // Buttons
        startBtn = Button(this).apply {
            text = "START MONITORING"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB")) // Royal Blue
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140
            ).apply { topMargin = 48 }
        }

        stopBtn = Button(this).apply {
            text = "STOP MONITORING"
            textSize = 14f
            setTextColor(Color.parseColor("#475569")) // Slate 600
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(2, Color.parseColor("#CBD5E1"))
                cornerRadius = 16f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140
            ).apply { topMargin = 16 }
            isEnabled = false
            alpha = 0.5f
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusCard)
        root.addView(startBtn)
        root.addView(stopBtn)

        setContentView(root)

        startBtn.setOnClickListener { beginPermissionFlow() }
        stopBtn.setOnClickListener {
            stopService(Intent(this, OpsService::class.java))
            updateUiActive(false)
        }
    }

    private fun missing(perms: Array<String>) = perms.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun beginPermissionFlow() {
        val stage1Missing = missing(stage1)
        if (stage1Missing.isNotEmpty()) {
            requestPermissions(stage1Missing.toTypedArray(), REQ_STAGE1); return
        }
        continueToStage2()
    }

    private fun continueToStage2() {
        val stage2Missing = if (Build.VERSION.SDK_INT >= 33) missing(stage2) else emptyList()
        if (stage2Missing.isNotEmpty()) {
            requestPermissions(stage2Missing.toTypedArray(), REQ_STAGE2); return
        }
        continueToStage3()
    }

    private fun continueToStage3() {
        val stage3Missing = if (Build.VERSION.SDK_INT >= 29) missing(stage3) else emptyList()
        if (stage3Missing.isNotEmpty()) {
            requestPermissions(stage3Missing.toTypedArray(), REQ_STAGE3); return
        }
        requestBatteryWhitelist()
    }

    private fun requestBatteryWhitelist() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (_: Throwable) { }
            }
        }
        launch()
    }

    private fun launch() {
        val intent = Intent(this, OpsService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        updateUiActive(true)
    }

    private fun updateUiActive(isActive: Boolean) {
        if (isActive) {
            statusText.text = "Monitoring Active"
            (statusDot.background as GradientDrawable).setColor(Color.parseColor("#10B981")) // Green
            startBtn.isEnabled = false; startBtn.alpha = 0.5f
            stopBtn.isEnabled = true; stopBtn.alpha = 1.0f
        } else {
            statusText.text = "System Standby"
            (statusDot.background as GradientDrawable).setColor(Color.parseColor("#EF4444")) // Red
            startBtn.isEnabled = true; startBtn.alpha = 1.0f
            stopBtn.isEnabled = false; stopBtn.alpha = 0.5f
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        when (code) {
            REQ_STAGE1 -> if (missing(stage1).isEmpty()) continueToStage2()
            REQ_STAGE2 -> continueToStage3()
            REQ_STAGE3 -> requestBatteryWhitelist()
        }
    }

    companion object {
        private const val REQ_STAGE1 = 11
        private const val REQ_STAGE2 = 12
        private const val REQ_STAGE3 = 13
    }
}
