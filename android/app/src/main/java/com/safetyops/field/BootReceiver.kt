package com.safetyops.field

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                val svc = Intent(context, OpsService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
                else context.startService(svc)
                Log.i("SafetyOps", "restarted after boot")
            } catch (t: Throwable) {
                Log.w("SafetyOps", "boot restart failed: ${t.message}")
            }
        }
    }
}
