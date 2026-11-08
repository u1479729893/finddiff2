package com.example.finddiff

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

class FindDiffService : Service() {

    companion object {
        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, FindDiffService::class.java)
            i.putExtra("code", code)
            i.putExtra("data", data)
            ctx.startForegroundService(i)
        }
    }

    private lateinit var wm: WindowManager
    private var btn: View? = null
    private var resultIv: ImageView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

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
            handler.postDelayed({ resultIv?.visibility = View.GONE }, 2000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (code != -1 && data != null) {
            try {
                startForeground(
                    1,
                    android.app.Notification.Builder(this, "ch").run {
                        setContentTitle("找茬识别")
                        setSmallIcon(android.R.drawable.ic_menu_search)
                        build()
                    }
                )
            } catch (_: Exception) {
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        btn?.let { wm.removeView(it) }
        resultIv?.let { wm.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
