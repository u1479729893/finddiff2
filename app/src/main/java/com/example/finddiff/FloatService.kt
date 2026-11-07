package com.example.finddiff

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast

class FloatService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var resultImageView: ImageView? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    private fun createOverlay() {
        // 悬浮窗容器
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val container = View(this)
        windowManager?.addView(container, params)
        overlayView = container

        // 结果图片显示
        val imgParams = WindowManager.LayoutParams(
            600, 800,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        imgParams.gravity = Gravity.CENTER
        val imgView = ImageView(this)
        windowManager?.addView(imgView, imgParams)
        resultImageView = imgView

        // 截帧按钮
        val btnParams = WindowManager.LayoutParams(
            300, 150,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        btnParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val btn = Button(this)
        btn.text = "识别"
        btn.textSize = 14f
        btn.setBackgroundColor(Color.parseColor("#CC000000"))
        btn.setTextColor(Color.WHITE)
        windowManager?.addView(btn, btnParams)

        btn.setOnClickListener {
            captureAndFind()
        }

        // 点击图片区域关闭
        imgView.setOnClickListener {
            hideResult()
        }
    }

    private fun captureAndFind() {
        if (!ProjectionHolder.hasPermission || ProjectionHolder.data == null) {
            Toast.makeText(this, "请先回到App授权", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(
                ProjectionHolder.resultCode,
                ProjectionHolder.data!!
            )

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            // 等一帧
            handler.postDelayed({
                try {
                    val bitmap = acquireBitmap()
                    if (bitmap != null) {
                        val result = findDifferences(bitmap)
                        resultImageView?.setImageBitmap(result)
                        bitmap.recycle()
                        // 3秒后隐藏结果
                        handler.postDelayed({ hideResult() }, 3000)
                    }
                    releaseProjection()
                } catch (e: Exception) {
                    Toast.makeText(this, "识别失败：${e.message}", Toast.LENGTH_SHORT).show()
                    releaseProjection()
                }
            }, 300)

        } catch (e: Exception) {
            Toast.makeText(this, "截帧失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acquireBitmap(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
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
            return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } finally {
            image.close()
        }
    }

    private fun findDifferences(fullBitmap: Bitmap): Bitmap {
        val width = fullBitmap.width
        val height = fullBitmap.height
        val leftBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width / 2, height)
        val rightBitmap = Bitmap.createBitmap(fullBitmap, width / 2, 0, width / 2, height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(fullBitmap, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        val blockSize = 60
        val threshold = 35

        for (y in 0 until height step blockSize) {
            for (x in 0 until width / 2 step blockSize) {
                if (isDifferent(leftBitmap, rightBitmap, x, y, blockSize, threshold)) {
                    canvas.drawRect(
                        x.toFloat(), y.toFloat(),
                        (x + blockSize).toFloat(), (y + blockSize).toFloat(),
                        paint
                    )
                }
            }
        }

        leftBitmap.recycle()
        rightBitmap.recycle()
        return result
    }

    private fun isDifferent(left: Bitmap, right: Bitmap, x: Int, y: Int, blockSize: Int, threshold: Int): Boolean {
        var diffCount = 0
        val maxDiff = (blockSize * blockSize) / 25

        for (by in y until minOf(y + blockSize, left.height)) {
            for (bx in x until minOf(x + blockSize, left.width)) {
                val lp = left.getPixel(bx, by)
                val rp = right.getPixel(bx, by)
                val diff = kotlin.math.abs(Color.red(lp) - Color.red(rp)) +
                           kotlin.math.abs(Color.green(lp) - Color.green(rp)) +
                           kotlin.math.abs(Color.blue(lp) - Color.blue(rp))
                if (diff > threshold) {
                    diffCount++
                    if (diffCount > maxDiff) return true
                }
            }
        }
        return false
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun hideResult() {
        resultImageView?.setImageBitmap(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProjection()
        overlayView?.let { windowManager?.removeView(it) }
        resultImageView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
