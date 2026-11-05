package com.example.finddiff

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class CaptureService : Service() {

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopCapture()
            Toast.makeText(this@CaptureService, "录屏捕获已停止", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handlerThread = HandlerThread("FindDiffCapture").apply { start() }
        handler = Handler(handlerThread.looper)
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == -1 || data == null) {
            showError("启动失败：录屏授权数据缺失（MainActivity 没传过来）")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // 关键：先拿 MediaProjection
            mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                showError("getMediaProjection 返回 null")
                stopSelf()
                return START_NOT_STICKY
            }

            // 关键：注册 callback
            mediaProjection?.registerCallback(mediaProjectionCallback, handler)

            startCapture()
            showFloatingButton()
        } catch (e: Exception) {
            showError("启动异常：\n${e.javaClass.simpleName}\n${e.message}")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            metrics.setTo(resources.displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
        }

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // 创建 ImageReader
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // 注册 ImageReader 回调
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val bitmap = imageToBitmap(image)
                    Toast.makeText(
                        this@CaptureService,
                        "已抓到一帧：${bitmap.width}x${bitmap.height}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    image.close()
                }
            }
        }, handler)

        // 创建虚拟屏 —— 原报错就在这儿
        try {
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
        } catch (e: Exception) {
            showError("createVirtualDisplay 炸了：\n${e.javaClass.simpleName}\n${e.message}")
            return
        }

        if (virtualDisplay == null) {
            showError("createVirtualDisplay 返回 null（没抛异常但没成功）")
            return
        }

        Toast.makeText(this, "捕获服务启动成功", Toast.LENGTH_SHORT).show()
    }

    private fun showFloatingButton() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 300
        params.y = 600

        floatingView = TextView(this).apply {
            text = "找"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(40, 40, 40, 40)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
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
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun showError(msg: String) {
        // 用 Toast 兜底，同时打印到 logcat
        Toast.makeText(this, "启动失败：$msg", Toast.LENGTH_LONG).show()
        android.util.Log.e("FindDiff", msg)
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "finddiff",
                "找茬助手",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
            Notification.Builder(this, "finddiff")
                .setContentTitle("找茬助手")
                .setContentText("录屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("找茬助手")
                .setContentText("录屏服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        floatingView?.let { windowManager.removeView(it) }
        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?) = null
}
