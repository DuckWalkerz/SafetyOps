package com.safetyops.field

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * The heart of the Android field app.
 *  - Foreground service, declared types location|camera.
 *  - Fused location, high accuracy, every 3s, uploaded to backend.
 *  - Front camera frames downscaled + JPEG'd + POSTed, at ~4 fps max.
 *  - Wakelock + notification with STOP action.
 */
class OpsService : Service() {

    private val io = Executors.newFixedThreadPool(3)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var owner: SimpleOwner
    private var locationCb: LocationCallback? = null
    private var provider: ProcessCameraProvider? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val lastFrameAt = AtomicLong(0L)

    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "android-${System.currentTimeMillis()}"
        } catch (_: Throwable) { "android-${System.currentTimeMillis()}" }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        owner = SimpleOwner()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(); return START_NOT_STICKY
        }
        startAsForeground()
        acquireWakeLock()
        startLocationTracking()
        startCameraStreaming()
        return START_STICKY
    }

    private fun startAsForeground() {
        val notif = buildNotification()
        when {
            Build.VERSION.SDK_INT >= 30 -> startForeground(
                Config.NOTIFICATION_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
            Build.VERSION.SDK_INT >= 29 -> startForeground(
                Config.NOTIFICATION_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            else -> startForeground(Config.NOTIFICATION_ID, notif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                Config.CHANNEL_ID, "SafetyOps",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OpsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val launch = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Config.CHANNEL_ID)
            .setContentTitle("SafetyOps active")
            .setContentText("Location + camera streaming")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(launch)
            .addAction(0, "STOP", stopIntent)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafetyOps::ops")
                .apply { setReferenceCounted(false); acquire(60 * 60 * 1000L) }
        }
    }

    private fun startLocationTracking() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Config.LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(Config.LOCATION_INTERVAL_MS / 2)
            .setMaxUpdateDelayMillis(Config.LOCATION_INTERVAL_MS * 2)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                val loc = r.lastLocation ?: return
                val battery = readBatteryPct()
                val speedMps = if (loc.hasSpeed()) loc.speed else 0f
                val bearing = if (loc.hasBearing()) loc.bearing else 0f
                val alt = if (loc.hasAltitude()) loc.altitude else 0.0
                val body = """{"lat":${loc.latitude},"lon":${loc.longitude},"acc":${loc.accuracy},"battery":$battery,"platform":"android","speed":$speedMps,"bearing":$bearing,"alt":$alt}"""
                io.execute { post("${Config.BASE_URL}/location?device=$deviceId", body.toByteArray(), "application/json") }
            }
        }
        locationCb = cb
        try {
            fused.requestLocationUpdates(req, cb, mainLooper)
        } catch (t: Throwable) { Log.w(TAG, "location: ${t.message}") }
    }

    private fun readBatteryPct(): Int {
        return try {
            val bm = getSystemService(BatteryManager::class.java)
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Throwable) { -1 }
    }

    private fun startCameraStreaming() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(io) { proxy -> handleFrame(proxy) }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (t: Throwable) {
                Log.w(TAG, "camera start failed: ${t.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameAt.get() < Config.FRAME_INTERVAL_MS) return
            lastFrameAt.set(now)

            val bmp: Bitmap = proxy.toBitmap()
            if (bmp.width == 0 || bmp.height == 0) return

            val targetW = Config.TARGET_WIDTH
            val targetH = (targetW.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            val out = ByteArrayOutputStream(targetW * targetH / 8)
            small.compress(Bitmap.CompressFormat.JPEG, Config.JPEG_QUALITY, out)
            if (small !== bmp) small.recycle()
            bmp.recycle()

            post("${Config.BASE_URL}/frame?device=$deviceId", out.toByteArray(), "image/jpeg")
        } catch (t: Throwable) {
            Log.w(TAG, "frame: ${t.message}")
        } finally {
            proxy.close()
        }
    }

    private fun post(urlStr: String, body: ByteArray, type: String) {
        try {
            val c = URL(urlStr).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = Config.UPLOAD_TIMEOUT_MS
            c.readTimeout = Config.UPLOAD_TIMEOUT_MS
            c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(body) }
            c.responseCode
            c.disconnect()
        } catch (_: Throwable) { /* swallow — next tick will retry */ }
    }

    override fun onDestroy() {
        locationCb?.let { runCatching { fused.removeLocationUpdates(it) } }
        provider?.unbindAll()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class SimpleOwner : LifecycleOwner {
        private val reg = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = reg
        init { reg.currentState = Lifecycle.State.RESUMED }
    }

    companion object {
        private const val TAG = "SafetyOps"
        const val ACTION_STOP = "com.safetyops.field.STOP"
    }
}
