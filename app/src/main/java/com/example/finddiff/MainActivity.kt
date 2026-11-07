package com.example.finddiff

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mpm: MediaProjectionManager

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                try {
                    val intent = Intent(this, CaptureService::class.java)
                    intent.putExtra("resultCode", result.resultCode)
                    intent.putExtra("data", result.data)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }

                    Toast.makeText(this, "已启动，回到游戏", Toast.LENGTH_SHORT).show()
                    moveTaskToBack(true)
                } catch (e: Exception) {
                    Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "启动失败：授权数据缺失", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btn = Button(this).apply {
            text = "启动找茬识别"
            textSize = 18f
        }
        setContentView(btn)

        btn.setOnClickListener {
            val intent = mpm.createScreenCaptureIntent()
            captureLauncher.launch(intent)
        }
    }
}
