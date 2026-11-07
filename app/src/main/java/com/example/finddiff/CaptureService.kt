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
import androidx.core.app.ServiceCompat

class CaptureService : Service() {

    private val NOTIFY_ID = 1
    private val CHANNEL_ID = "finddiff_channel"
    private lateinit var mpm: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createChannel()
        val notify = buildNotification("初始化中")
        ServiceCompat.startForeground(
            this,
            NOTIFY_ID,
            notify,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            cleanStop()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("resultCode", -1)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }

        if (resultCode == -1 || data == null) {
            updateNotification("授权数据缺失，已停止")
            cleanStop()
            return START_NOT_STICKY
        }

        try {
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            updateNotification("录屏已连接，回游戏")
        } catch (e: Exception) {
            updateNotification("MediaProjection 失败：${e.message}")
            cleanStop()
        }
        return START_NOT_STICKY
    }

    private fun cleanStop() {
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "找茬识别",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("找茬识别")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFY_ID, buildNotification(text))
    }
}
