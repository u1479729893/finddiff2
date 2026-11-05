package com.example.finddiff

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mpm: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btn = Button(this).apply {
            text = "启动找茬识别"
            textSize = 18f
        }
        setContentView(btn)

        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ),
                    1001
                )
            } else {
                startActivityForResult(mpm.createScreenCaptureIntent(), 1002)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startActivityForResult(mpm.createScreenCaptureIntent(), 1002)
            } else {
                Toast.makeText(this, "悬浮窗权限未开启", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (requestCode == 1002 && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "已启动，回到游戏界面等待识别", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "录屏授权被取消", Toast.LENGTH_SHORT).show()
        }
    }
}
