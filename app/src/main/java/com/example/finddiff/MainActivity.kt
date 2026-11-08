package com.example.finddiff

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ar ->
            if (ar.resultCode == RESULT_OK && ar.data != null) {
                try {
                    FindDiffService.start(this, ar.resultCode, ar.data!!)
                    Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            Toast.makeText(this, "布局加载失败：${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        val btn = findViewById<Button?>(R.id.btnStart)
        if (btn == null) {
            Toast.makeText(this, "btnStart 找不到", Toast.LENGTH_LONG).show()
            return
        }

        btn.setOnClickListener {
            try {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                launcher.launch(mpm.createScreenCaptureIntent())
            } catch (e: Exception) {
                Toast.makeText(this, "录屏授权失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
