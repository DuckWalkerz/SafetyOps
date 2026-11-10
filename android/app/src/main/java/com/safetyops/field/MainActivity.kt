package com.safetyops.field

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var status: TextView

    private val stage1 = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )
    private val stage2 = arrayOf(Manifest.permission.POST_NOTIFICATIONS)   // API 33+
    private val stage3 = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)  // API 29+

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#080b10"))
        }
        val title = TextView(this).apply {
            text = "SAFETYOPS"
            textSize = 22f
            setTextColor(Color.parseColor("#10b981"))
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Idle — tap START to arm"
            textSize = 14f
            setTextColor(Color.parseColor("#e5e7eb"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        val start = Button(this).apply { text = "START OPS" }
        val stop  = Button(this).apply { text = "STOP OPS" }

        root.addView(title); root.addView(status); root.addView(start); root.addView(stop)
        setContentView(root)

        start.setOnClickListener { beginPermissionFlow() }
        stop.setOnClickListener {
            stopService(Intent(this, OpsService::class.java))
            status.text = "Stopped"
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
                } catch (_: Throwable) { /* user can whitelist manually */ }
            }
        }
        launch()
    }

    private fun launch() {
        val intent = Intent(this, OpsService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "OPS ACTIVE — tracking + streaming"
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        when (code) {
            REQ_STAGE1 -> {
                if (missing(stage1).isEmpty()) continueToStage2()
                else status.text = "Location + camera required"
            }
            REQ_STAGE2 -> continueToStage3()
            REQ_STAGE3 -> {
                // Even if the user denies background location, we still start — the service
                // will work while the app is foregrounded and will degrade gracefully.
                requestBatteryWhitelist()
            }
        }
    }

    companion object {
        private const val REQ_STAGE1 = 11
        private const val REQ_STAGE2 = 12
        private const val REQ_STAGE3 = 13
    }
}
