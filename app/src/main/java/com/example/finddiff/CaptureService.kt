package com.example.finddiff

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class CaptureService : Service() {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var callbackRegistered = false

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode == -1 || data == null) {
            Toast.makeText(this, "启动捕获失败：缺少录屏授权", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, createNotification())

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

        // 关键：必须先注册 callback，再 start capture
        if (!callbackRegistered) {
            mediaProjection?.registerCallback(mediaProjectionCallback, handler)
            callbackRegistered = true
        }

        setupFloatingWindow()
        startCapture()

        return START_STICKY
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutInflater = LayoutInflater.from(this)
        floatingView = layoutInflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        windowManager?.addView(floatingView, params)

        val btn = floatingView?.findViewById<TextView>(R.id.floating_btn)
        btn?.setOnClickListener {
            captureOnce()
        }

        btn?.setOnLongClickListener {
            stopSelf()
            true
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        btn?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FindDiffCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun captureOnce() {
        val reader = imageReader ?: return
        val image: Image? = reader.acquireLatestImage()
        if (image != null) {
            try {
                val bitmap = imageToBitmap(image)
                // TODO: 你后面找茬识别逻辑放这里
                Toast.makeText(this, "已抓到一帧：${bitmap.width}x${bitmap.height}", Toast.LENGTH_SHORT).show()
            } finally {
                image.close()
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    private fun createNotification() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "finddiff",
                "找茬助手",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)

            android.app.Notification.Builder(this, "finddiff")
                .setContentTitle("找茬助手")
                .setContentText("录屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("找茬助手")
                .setContentText("录屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        floatingView?.let { windowManager?.removeView(it) }
    }
}
