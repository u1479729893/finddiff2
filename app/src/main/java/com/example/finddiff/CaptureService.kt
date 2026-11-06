package com.example.finddiff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log

class CaptureService : Service() {

    private val TAG = "CaptureService"
    private val NOTIFY_ID = 1
    private val CHANNEL_ID = "finddiff_channel"

    private lateinit var mpm: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        startForeground(NOTIFY_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val resultCode = intent.getIntExtra("resultCode", -1)
        val data = intent.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            Log.e(TAG, "授权数据缺失")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            Log.d(TAG, "MediaProjection 创建成功")

            // TODO: 这里接 ImageReader / 截屏 / 找茬逻辑
            // 现在先只保持前台服务运行

        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 失败: ${e.message}")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "找茬识别",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("找茬识别运行中")
            .setContentText("返回游戏即可使用")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }
}
