package cn.edu.buaa.ledger.capture

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.Image
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import cn.edu.buaa.ledger.R
import cn.edu.buaa.ledger.recognition.ReceiptActivity
import kotlin.math.max

/** Exactly one virtual display per fresh consent token. Every exit releases all capture resources. */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latestFrame: Image? = null
    private var finished = false
    private var readyAt = 0L
    private var width = 0
    private var height = 0
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { finishCapture("截图已被系统停止，请重新授权或使用相册。") }
        override fun onCapturedContentResize(w: Int, h: Int) {
            if (!finished) runCatching { resize(w, h) }.onFailure { finishCapture("画面尺寸改变，截图失败，请重试。") }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finishCapture("已取消截图，没有新增账目。"); return START_NOT_STICKY }
        if (projection != null || finished) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "账单截图", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(2201, NotificationCompat.Builder(this, "capture").setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("正在截取一张账单").setContentText("约2秒后捕获，完成后立即停止")
            .setOngoing(true).addAction(0, "取消", stop).build())
        try {
            val data = intent?.let { IntentCompat.getParcelableExtra(it, "consent", Intent::class.java) }
                ?: error("Missing consent")
            val token = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("resultCode", 0), data)
            projection = token
            token.registerCallback(callback, handler)
            val wm = getSystemService(WindowManager::class.java)
            val size = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
                else android.util.DisplayMetrics().let { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it); it.widthPixels to it.heightPixels }
            resize(size.first, size.second)
            readyAt = SystemClock.elapsedRealtime() + 2000
            display = token.createVirtualDisplay("Receipt single frame", width, height, resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
            handler.postDelayed({ finishCapture("未取得有效画面，请使用相册或分享入口。") }, 15000)
            // Static screens need polling too: no new frame may arrive after the initial delay.
            handler.postDelayed(object : Runnable {
                override fun run() { if (!finished) { readFrame(); handler.postDelayed(this, 150) } }
            }, 2100)
        } catch (failure: Exception) { finishCapture("截图启动失败，请重新授权或使用相册。") }
        return START_NOT_STICKY
    }
    private fun resize(w: Int, h: Int) {
        require(w > 0 && h > 0)
        val scale = max(1.0, max(w, h) / 2000.0)
        val newW = (w / scale).toInt().coerceAtLeast(1)
        val newH = (h / scale).toInt().coerceAtLeast(1)
        if (newW == width && newH == height) return
        val next = ImageReader.newInstance(newW, newH, PixelFormat.RGBA_8888, 3)
        // Drain frames while consent/task transitions settle. Otherwise the bounded queue can
        // retain the protected consent screen and stall before the target app gets rendered.
        next.setOnImageAvailableListener({ source ->
            if (!finished && source === reader) {
                try {
                    source.acquireLatestImage()?.let { frame -> latestFrame?.close(); latestFrame = frame }
                    readFrame()
                } catch (_: Exception) { finishCapture("截图画面已失效，请重新授权。") }
            }
        }, handler)
        display?.surface = null
        latestFrame?.close(); latestFrame = null
        reader?.close()
        reader = next; width = newW; height = newH
        display?.resize(width, height, resources.configuration.densityDpi)
        display?.surface = next.surface
        readyAt = max(readyAt, SystemClock.elapsedRealtime() + 2000)
    }
    private fun readFrame() {
        if (finished || SystemClock.elapsedRealtime() < readyAt) return
        try {
            val frame = latestFrame ?: return
            latestFrame = null
            val bitmap = frame.use {
                val plane = it.planes[0]
                val paddedWidth = plane.rowStride / plane.pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888)
                try { padded.copyPixelsFromBuffer(plane.buffer); Bitmap.createBitmap(padded, 0, 0, it.width, it.height).let { b -> if (b === padded) padded.copy(Bitmap.Config.ARGB_8888, false) else b } }
                finally { padded.recycle() }
            }
            try {
                var brightest = 0
                for (y in 0 until bitmap.height step 24) for (x in 0 until bitmap.width step 24) {
                    val pixel = bitmap.getPixel(x, y)
                    brightest = max(brightest, max(android.graphics.Color.red(pixel), max(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))))
                }
                if (brightest < 25) finishCapture("画面为黑屏，可能受到应用保护；请使用可导出的图片或手动记账。")
                else { CaptureStore(this).save(bitmap); finishCapture(null) }
            } finally { bitmap.recycle() }
        } catch (failure: Exception) { finishCapture("画面读取失败，请重新截图或使用相册。") }
    }
    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        if (Build.VERSION.SDK_INT < 34) finishCapture("屏幕方向已改变，请重新截图。")
    }
    private fun finishCapture(error: String?) {
        if (finished) return
        finished = true
        if (error != null) CaptureStore(this).failure(error)
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, ReceiptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "capture").setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(if (error == null) "截图完成，点击核对账单" else "截图未完成")
            .setContentText(error ?: "打开图片记账页后选择‘打开最近截图’，确认后才会入账。")
            .setContentIntent(open).setAutoCancel(true).build()
        try { getSystemService(NotificationManager::class.java).notify(2202, notification) } catch (_: SecurityException) { /* Page remains a result entry point. */ }
        stopSelf()
    }
    private fun release() {
        handler.removeCallbacksAndMessages(null)
        display?.release(); display = null
        latestFrame?.close(); latestFrame = null
        reader?.close(); reader = null
        projection?.let { it.unregisterCallback(callback); it.stop() }; projection = null
    }
    override fun onDestroy() { finished = true; release(); super.onDestroy() }
}
