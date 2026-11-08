package com.example.finddiff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast

class FindDiffService : Service() {

    companion object {
        private const val CHANNEL_ID = "finddiff_channel"

        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, FindDiffService::class.java)
            i.putExtra("code", code)
            i.putExtra("data", data)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Service启动失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private lateinit var wm: WindowManager
    private var btn: View? = null
    private var resultIv: ImageView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        try {
            addFloatView()
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗失败：${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            startForeground(1, buildNotification())
        } catch (e: Exception) {
            Toast.makeText(this, "前台服务失败：${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun addFloatView() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 60
        lp.y = 240

        btn = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        wm.addView(btn, lp)

        val ivLp = WindowManager.LayoutParams(
            900,
            1600,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        ivLp.gravity = Gravity.CENTER
        resultIv = ImageView(this).apply { visibility = View.GONE }
        wm.addView(resultIv, ivLp)

        val findBtn = btn?.findViewById<Button>(R.id.btnStart)
        findBtn?.setOnClickListener {
            Toast.makeText(this, "点击了悬浮按钮", Toast.LENGTH_SHORT).show()
            handler.postDelayed({ resultIv?.visibility = View.GONE }, 2000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "找茬服务",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("找茬识别")
                .setContentText("服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("找茬识别")
                .setContentText("服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { btn?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { resultIv?.let { wm.removeView(it) } } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
