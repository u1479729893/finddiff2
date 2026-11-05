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
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        handlerThread = HandlerThread("FindDiffCapture")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        startForeground(1, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingButton()

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            Toast.makeText(this, "启动捕获失败：无录屏授权", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        startCapture()
    }

    private fun showFloatingButton() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        val btn = floatingView?.findViewById<TextView>(R.id.floating_btn)

        btn?.setOnClickListener {
            captureOnce()
        }

        btn?.setOnLongClickListener {
            stopSelf()
            Toast.makeText(this, "已关闭", Toast.LENGTH_SHORT).show()
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

        windowManager?.addView(floatingView, params)
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dm = resources.displayMetrics
            metrics.setTo(dm)
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
        }

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // 1. 先创建 ImageReader
        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        // 2. 关键：createVirtualDisplay 前必须先注册 callback
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val bitmap = imageToBitmap(image)
                    Toast.makeText(
                        this@CaptureService,
                        "抓到画面 ${bitmap.width}x${bitmap.height}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    image.close()
                }
            }
        }, handler)

        // 3. 再创建 VirtualDisplay，绑定 surface
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FindDiffCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        if (virtualDisplay == null) {
            Toast.makeText(this, "启动捕获失败：创建虚拟屏幕失败", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun captureOnce() {
        // 现在靠 ImageReader callback 自动收帧；这里只提示一下
        Toast.makeText(this, "已触发抓帧，等一帧", Toast.LENGTH_SHORT).show()
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
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
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
        handlerThread.quitSafely()
    }
}
