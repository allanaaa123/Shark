
package com.example.sharkoverlay

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var preview: SharkPreviewView
    private lateinit var count: TextView
    private val sharks = mutableListOf<SharkConfig>()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shark", MODE_PRIVATE)

        try {
            val saved = JSONArray(prefs.getString("sharks", "[]") ?: "[]")
            for (i in 0 until minOf(10, saved.length())) {
                val obj = saved.getJSONObject(i)
                sharks.add(
                    SharkConfig.fromSaved(
                        obj.optInt("size", 100),
                        obj.optInt("speed", 100),
                        obj.optString("species", "")
                    )
                )
            }
        } catch (_: Exception) {}

        if (!prefs.contains("sharks")) {
            sharks.add(SharkConfig(100, 100))
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "桌面海洋寵物"
            textSize = 27f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "選擇雙髻鯊、鯨鯊或殺人鯨，每隻可獨立設定大小和游速"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(14))
        })

        count = TextView(this).apply { textSize = 18f }
        root.addView(count)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(list)

        root.addView(Button(this).apply {
            text = "＋ 新增鯊魚／鯨魚"
            setOnClickListener {
                if (sharks.size < 10) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("選擇要新增的寵物")
                        .setItems(SharkSpecies.entries.map { it.displayName }.toTypedArray()) { _, which ->
                            if (sharks.size < 10) {
                                sharks.add(SharkConfig(species = SharkSpecies.entries[which]))
                                save()
                                refresh()
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "最多 10 隻",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        root.addView(TextView(this).apply {
            text = "即時預覽"
            textSize = 19f
            setPadding(0, dp(20), 0, dp(8))
        })

        preview = SharkPreviewView(this)
        root.addView(
            preview,
            LinearLayout.LayoutParams(-1, dp(190))
        )

        root.addView(Button(this).apply {
            text = "啟動 / 更新桌面寵物"
            setOnClickListener {
                save()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "請允許顯示在其他應用程式上層，返回後再啟動",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    startForegroundService(
                        Intent(this@MainActivity, SharkService::class.java)
                            .setAction("START")
                    )
                }
            }
        })

        root.addView(Button(this).apply {
            text = "停止所有寵物"
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, SharkService::class.java)
                        .setAction("STOP")
                )
            }
        })

        root.addView(TextView(this).apply {
            text = "寵物會在其他 App 上方游動，不會阻擋觸控。"
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        })

        setContentView(scroll)
        refresh()
    }

    private fun save() {
        val arr = JSONArray()
        sharks.forEach {
            arr.put(
                JSONObject()
                    .put("size", it.size)
                    .put("speed", it.speed)
                    .put("species", it.species.id)
            )
        }
        getSharedPreferences("shark", MODE_PRIVATE)
            .edit()
            .putString("sharks", arr.toString())
            .apply()
    }

    private fun refresh() {
        count.text = "海洋寵物：${sharks.size} / 10 隻"
        list.removeAllViews()

        sharks.forEachIndexed { index, shark ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(10))
                setBackgroundColor(Color.rgb(238, 238, 238))
            }

            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            heading.addView(
                TextView(this).apply {
                    text = "${index + 1}. ${shark.species.displayName}"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            heading.addView(Button(this).apply {
                text = "刪除"
                setOnClickListener {
                    sharks.removeAt(index)
                    save()
                    refresh()
                }
            })
            box.addView(heading)

            fun addSlider(
                title: String,
                min: Int,
                max: Int,
                initial: Int,
                onValue: (Int) -> Unit
            ) {
                val label = TextView(this).apply {
                    text = "$title：$initial%"
                }
                box.addView(label)

                box.addView(SeekBar(this).apply {
                    this.max = max - min
                    progress = initial - min
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                val value = min + progress
                                label.text = "$title：$value%"
                                onValue(value)
                                save()
                                preview.setSharks(sharks.toList())
                            }

                            override fun onStartTrackingTouch(
                                seekBar: SeekBar?
                            ) {}

                            override fun onStopTrackingTouch(
                                seekBar: SeekBar?
                            ) {}
                        }
                    )
                })
            }

            addSlider("大小", 30, 200, shark.size) {
                shark.size = it
            }
            addSlider("游速", 20, 200, shark.speed) {
                shark.speed = it
            }

            list.addView(
                box,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                }
            )
        }

        preview.setSharks(sharks.toList())
    }
}

