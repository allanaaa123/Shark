
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
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var listToggle: Button
    private lateinit var pageScroll: ScrollView
    private var listExpanded = false
    private lateinit var preview: SharkPreviewView
    private lateinit var count: TextView
    private val sharks = mutableListOf<SharkConfig>()
    private var shakeEnabled = false
    private val shakeSensor by lazy {
        ShakeSensor(this) { if (shakeEnabled && ::preview.isInitialized) preview.startle() }
    }

    override fun onResume() {
        super.onResume()
        if (shakeEnabled) shakeSensor.start()
    }

    override fun onPause() {
        shakeSensor.stop()
        super.onPause()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shark", MODE_PRIVATE)
        listExpanded = prefs.getBoolean("shark_list_expanded", false)

        try {
            val saved = JSONArray(prefs.getString("sharks", "[]") ?: "[]")
            for (i in 0 until SharkConfig.savedCount(saved.length())) {
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

        pageScroll = ScrollView(this)
        val scroll = pageScroll
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
            text = "選擇 9 種海洋寵物，每隻可獨立設定大小和游速"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(14))
        })

        count = TextView(this).apply { textSize = 18f }
        root.addView(count)

        root.addView(Button(this).apply {
            text = "🎲 隨機新增 N 隻"
            setOnClickListener { showRandomAddDialog() }
        })

        listToggle = Button(this).apply {
            setOnClickListener { changeListExpansion(!listExpanded) }
        }
        root.addView(listToggle)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(list)

        root.addView(Button(this).apply {
            text = "＋ 新增鯊魚／鯨魚"
            setOnClickListener {
                if (SharkConfig.canAdd(sharks.size)) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("選擇要新增的寵物")
                        .setItems(SharkSpecies.entries.map { it.displayName }.toTypedArray()) { _, which ->
                            if (SharkConfig.canAdd(sharks.size)) {
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
                        "最多 ${SharkConfig.MAX_PETS} 隻",
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
        val initialEffects = EffectsStrength.fromId(prefs.getString(EffectsStrength.PREFERENCE, null))
        preview.applyEffectsStrength(initialEffects)
        val initialSocial = SocialMode.fromId(prefs.getString(SocialMode.PREFERENCE, null))
        preview.applySocialMode(initialSocial)
        root.addView(
            preview,
            LinearLayout.LayoutParams(-1, dp(190))
        )

        root.addView(TextView(this).apply {
            text = "水中特效"
            textSize = 18f
            setPadding(0, dp(16), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "立即套用到預覽與桌面寵物，轉彎時氣泡較多。"
            textSize = 13f
        })
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            EffectsStrength.entries.forEach { strength ->
                addView(RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = strength.label
                    tag = strength
                    isChecked = strength == initialEffects
                })
            }
            setOnCheckedChangeListener { group, checkedId ->
                val selected = group.findViewById<RadioButton>(checkedId)?.tag as? EffectsStrength
                if (selected != null) {
                    prefs.edit().putString(EffectsStrength.PREFERENCE, selected.id).apply()
                    preview.applyEffectsStrength(selected)
                    sendBroadcast(Intent("$packageName.EFFECTS_CHANGED").setPackage(packageName))
                }
            }
        })

        root.addView(TextView(this).apply {
            text = "魚群互動"
            textSize = 18f
            setPadding(0, dp(16), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "立即套用並保存。輕柔互動，仍會偶爾交錯重疊。"
            textSize = 13f
        })
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            SocialMode.entries.forEach { mode ->
                addView(RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = "${mode.label}：${mode.description}"
                    textSize = 14f
                    tag = mode
                    isChecked = mode == initialSocial
                })
            }
            setOnCheckedChangeListener { group, checkedId ->
                val selected = group.findViewById<RadioButton>(checkedId)?.tag as? SocialMode
                if (selected != null) {
                    prefs.edit().putString(SocialMode.PREFERENCE, selected.id).apply()
                    preview.applySocialMode(selected)
                    sendBroadcast(Intent("$packageName.SOCIAL_CHANGED").setPackage(packageName))
                }
            }
        })

        shakeEnabled = prefs.getBoolean(ShakeSensor.PREFERENCE, false) && shakeSensor.isAvailable
        root.addView(Switch(this).apply {
            text = "搖動驚嚇"
            textSize = 18f
            setPadding(0, dp(16), 0, dp(4))
            isChecked = shakeEnabled
            isEnabled = shakeSensor.isAvailable
            setOnCheckedChangeListener { _, enabled ->
                shakeEnabled = enabled
                prefs.edit().putBoolean(ShakeSensor.PREFERENCE, enabled).apply()
                if (enabled) shakeSensor.start() else {
                    shakeSensor.stop()
                    preview.stopStartle()
                }
                sendBroadcast(Intent("$packageName.SHAKE_CHANGED").setPackage(packageName))
            }
        })
        root.addView(TextView(this).apply {
            text = if (shakeSensor.isAvailable)
                "開啟後搖動手機，魚會短暫加速散開，再恢復游速。觸發間隔至少 4 秒，熄屏暫停偵測。"
                else "此裝置沒有加速度感測器，無法使用搖動驚嚇。"
            textSize = 13f
        })

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
            text = "寵物會在其他 App 上方游動。安全性畫面若限制操作，可由通知停止寵物。"
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        })

        setContentView(scroll)
        refresh()
    }

    private fun showRandomAddDialog() {
        val remaining = RandomPetBatch.remainingSlots(sharks.size)
        if (remaining == 0) {
            Toast.makeText(this, "已達上限 ${SharkConfig.MAX_PETS} 隻", Toast.LENGTH_SHORT).show()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        content.addView(TextView(this).apply {
            text = "目前 ${sharks.size} 隻，還可新增 $remaining 隻。\n從 ${SharkSpecies.entries.size} 種海洋寵物隨機挑選，可重複；保留現有寵物。"
            textSize = 14f
        })
        val number = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            hint = "新增數量：1～$remaining"
            setText(minOf(5, remaining).toString())
            selectAll()
        }
        content.addView(number)
        val prefs = getSharedPreferences("shark", MODE_PRIVATE)
        var variation = BatchVariation.fromId(prefs.getString(BatchVariation.PREFERENCE, null))
        content.addView(TextView(this).apply {
            text = "大小與速度"
            textSize = 16f
            setPadding(0, dp(12), 0, 0)
        })
        content.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            BatchVariation.entries.forEach { option ->
                addView(RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = option.label
                    textSize = 14f
                    tag = option
                    isChecked = option == variation
                })
            }
            setOnCheckedChangeListener { group, checkedId ->
                (group.findViewById<RadioButton>(checkedId)?.tag as? BatchVariation)?.let {
                    variation = it
                }
            }
        })
        content.addView(TextView(this).apply {
            text = "常態分佈以 100% 為中心，大小標準差 25%、速度 20%。各自抽樣，多數接近 100%，超出滑桿範圍會重抽。"
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("隨機新增寵物")
            .setView(scroll)
            .setPositiveButton("新增", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val requested = number.text.toString().trim().toIntOrNull()
                val available = RandomPetBatch.remainingSlots(sharks.size)
                if (requested == null || requested !in 1..available) {
                    number.error = "請輸入 1～$available 的整數"
                    return@setOnClickListener
                }
                val additions = RandomPetBatch.generate(requested, sharks.size, variation = variation)
                sharks.addAll(additions)
                prefs.edit().putString(BatchVariation.PREFERENCE, variation.id).apply()
                save()
                refresh()
                dialog.dismiss()
                Toast.makeText(this, "已隨機新增 $requested 隻", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
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
        count.text = "海洋寵物：${sharks.size} / ${SharkConfig.MAX_PETS} 隻"
        list.removeAllViews()
        applyListExpansion()
        preview.setSharks(sharks.toList())
    }

    private fun changeListExpansion(expanded: Boolean) {
        listExpanded = expanded
        getSharedPreferences("shark", MODE_PRIVATE).edit()
            .putBoolean("shark_list_expanded", expanded).apply()
        applyListExpansion()
        if (!expanded) listToggle.post {
            pageScroll.smoothScrollTo(0, listToggle.top)
        }
    }

    private fun applyListExpansion() {
        listToggle.text = if (listExpanded) "▼ 收合鯊魚清單（${sharks.size} 隻）"
            else "▶ 展開鯊魚清單（${sharks.size} 隻）"
        list.visibility = if (listExpanded) View.VISIBLE else View.GONE
        // Populate controls only when opened; folding does not touch the preview.
        if (listExpanded && list.childCount == 0) renderSharkCards()
    }

    private fun renderSharkCards() {
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

        if (sharks.isNotEmpty()) {
            list.addView(Button(this).apply {
                text = "▲ 收合鯊魚清單"
                setOnClickListener { changeListExpansion(false) }
            })
        } else {
            list.addView(TextView(this).apply {
                text = "還沒有寵物，可以使用上方按鈕新增。"
                setPadding(0, dp(8), 0, dp(8))
            })
        }
    }
}
