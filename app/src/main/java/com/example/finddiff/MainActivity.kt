package com.example.finddiff

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ar ->
            if (ar.resultCode == RESULT_OK && ar.data != null) {
                FindDiffService.start(this, ar.resultCode, ar.data!!)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btn = findViewById<Button>(R.id.btnStart)
        btn.setOnClickListener {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            launcher.launch(mpm.createScreenCaptureIntent())
        }
    }
}
