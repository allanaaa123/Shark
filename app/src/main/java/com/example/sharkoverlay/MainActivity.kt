package com.example.sharkoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shark", MODE_PRIVATE)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 55, 40, 20) }
        layout.addView(TextView(this).apply { text = "黑白像素桌面鯊魚"; textSize = 25f })
        val species = Spinner(this)
        species.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("一般鯊魚", "錘頭鯊"))
        species.setSelection(prefs.getInt("species", 0))
        layout.addView(species)
        val perspective = Spinner(this)
        perspective.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("俯視", "側視偏俯視"))
        perspective.setSelection(prefs.getInt("perspective", 1))
        layout.addView(perspective)
        layout.addView(TextView(this).apply { text = "鯊魚大小" })
        val size = SeekBar(this).apply { max = 160; progress = prefs.getInt("size", 90) - 40 }
        layout.addView(size)
        layout.addView(TextView(this).apply { text = "游泳速度" })
        val speed = SeekBar(this).apply { max = 100; progress = prefs.getInt("speed", 45) }
        layout.addView(speed)
        fun save() { prefs.edit().putInt("species", species.selectedItemPosition).putInt("perspective", perspective.selectedItemPosition).putInt("size", size.progress + 40).putInt("speed", speed.progress).apply() }
        layout.addView(Button(this).apply { text = "啟動 / 更新鯊魚"; setOnClickListener {
            save()
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this@MainActivity, "請允許顯示在其他應用程式上層，返回後再按啟動", Toast.LENGTH_LONG).show()
            } else startForegroundService(Intent(this@MainActivity, SharkService::class.java).setAction("START"))
        } })
        layout.addView(Button(this).apply { text = "停止鯊魚"; setOnClickListener { startService(Intent(this@MainActivity, SharkService::class.java).setAction("STOP")) } })
        layout.addView(TextView(this).apply { text = "鯊魚會在其他 App 上層游動。透明區域可正常操作；觸摸鯊魚所在的小視窗可能攔截觸控。可從通知停止。" })
        setContentView(layout)
    }
}
