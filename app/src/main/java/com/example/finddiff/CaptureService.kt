package com.example.finddiff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var dotView: ImageView? = null
    private var tipView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = object : Runnable {
        override fun run() {
            try {
                grabFrame()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 1200)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        showOverlay()

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            Toast.makeText(this, "启动失败：授权数据缺失", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        mediaProjection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(resultCode, data)

        setupVirtualDisplay()
        handler.post(captureRunnable)
        Toast.makeText(this, "找茬识别已启动", Toast.LENGTH_SHORT).show()

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            metrics.setTo(resources.displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
        }

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FindDiff",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun grabFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val real = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()

            val diff = findDiff(real)
            if (diff != null) {
                markDot(diff.first, diff.second)
            } else {
                clearDot()
            }

            real.recycle()
        } finally {
            image.close()
        }
    }

    private fun findDiff(bitmap: Bitmap): Pair<Int, Int>? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 200 || h < 200) return null

        val leftW = w / 2 - 20
        val rightX = w / 2 + 20
        val step = 24
        var bestX = -1
        var bestY = -1
        var maxDiff = 0

        for (y in 80 until h - 80 step step) {
            for (x in 80 until leftW step step) {
                val c1 = bitmap.getPixel(x, y)
                val c2 = bitmap.getPixel(rightX + (x - 80), y)
                val d = colorDistance(c1, c2)
                if (d > maxDiff) {
                    maxDiff = d
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (maxDiff > 45) Pair(bestX, bestY) else null
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val r1 = Color.red(c1); val g1 = Color.green(c1); val b1 = Color.blue(c1)
        val r2 = Color.red(c2); val g2 = Color.green(c2); val b2 = Color.blue(c2)
        return kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        overlayView = FrameLayout(this)
        dotView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
            alpha = 0f
        }
        tipView = TextView(this).apply {
            text = "识别中..."
            setTextColor(Color.YELLOW)
            textSize = 16f
            alpha = 0.8f
        }

        overlayView?.addView(dotView)
        overlayView?.addView(tipView)
        windowManager.addView(overlayView, params)
    }

    private fun markDot(x: Int, y: Int) {
        dotView?.let {
            it.x = x.toFloat() - 24
            it.y = y.toFloat() - 24
            it.alpha = 1f
        }
        tipView?.let {
            it.text = "差异点：$x,$y"
            it.x = x.toFloat() + 20
            it.y = y.toFloat() - 40
        }
    }

    private fun clearDot() {
        dotView?.alpha = 0f
        tipView?.text = "识别中..."
    }

    private fun createNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("finddiff", "找茬助手", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, "finddiff")
            .setContentTitle("找茬助手运行中")
            .setContentText("返回游戏界面，等待识别标记")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureRunnable)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        overlayView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
