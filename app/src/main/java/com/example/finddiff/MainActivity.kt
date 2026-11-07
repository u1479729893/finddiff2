package com.example.finddiff

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : Activity() {

    private lateinit var mpm: MediaProjectionManager

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                // 把授权结果存到单例，后面截帧用
                ProjectionHolder.resultCode = result.resultCode
                ProjectionHolder.data = result.data
                ProjectionHolder.hasPermission = true

                Toast.makeText(this, "授权成功，可以开始找茬了", Toast.LENGTH_SHORT).show()
                // 启动悬浮窗服务
                startService(Intent(this, FloatService::class.java))
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "需要录屏权限才能截帧", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btn = Button(this)
        btn.text = "启动找茬识别"
        btn.textSize = 18f
        setContentView(btn)

        btn.setOnClickListener {
            if (ProjectionHolder.hasPermission) {
                // 已授权，直接启动悬浮窗
                startService(Intent(this, FloatService::class.java))
                moveTaskToBack(true)
            } else {
                // 首次需要授权
                launcher.launch(mpm.createScreenCaptureIntent())
            }
        }
    }
}
