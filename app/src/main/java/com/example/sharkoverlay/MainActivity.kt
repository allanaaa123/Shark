package com.example.sharkoverlay

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Three focused pages; data changes update the running service without recreating pets. */
class MainActivity : Activity() {
    private lateinit var model: PetListModel
    private lateinit var preview: SharkPreviewView
    private lateinit var host: FrameLayout
    private lateinit var oceanPage: View
    private lateinit var creaturePage: View
    private lateinit var settingsPage: View
    private lateinit var status: TextView
    private lateinit var total: TextView
    private lateinit var creatureTotal: TextView
    private lateinit var startStop: Button
    private lateinit var socialShortcut: Button
    private lateinit var grid: GridLayout
    private lateinit var creatureScroll: ScrollView
    private lateinit var empty: TextView
    private lateinit var selectToggle: Button
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectedCount: TextView
    private lateinit var undoBar: LinearLayout
    private lateinit var undoText: TextView
    private val navButtons = mutableListOf<Button>()
    private val pages = mutableListOf<View>()
    private var currentTab = 0
    private var query = ""
    private var filter = PetFilter.ALL
    private var selecting = false
    private val selected = linkedSetOf<String>()
    private var removed = emptyList<RemovedPet>()
    private var undoDeadline = 0L
    private var undoWasRunning = false
    private var gridDirty = true
    private var dataDirty = false
    private val filterButtons = mutableMapOf<PetFilter,Button>()
    private var commitQueued = false
    private var sheet: Dialog? = null
    private val main = Handler(Looper.getMainLooper())
    private val commit = Runnable { commitChanges() }
    private val expireUndo = Runnable { removed = emptyList(); undoBar.visibility = View.GONE }
    private var receiverRegistered = false
    private val prefs by lazy { getSharedPreferences("shark", MODE_PRIVATE) }
    private var shakeEnabled = false
    private val shakeSensor by lazy {
        ShakeSensor(this) { if (shakeEnabled && currentTab == 0) preview.startle() }
    }
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateSummary() }
    }
    private fun dp(n: Int) = OceanUi.dp(this,n)
    private fun column(padding: Int = 0) = OceanUi.column(this,padding)
    private fun text(s: String, size: Float = 14f, bold: Boolean = false, color: Int = OceanUi.ink) =
        OceanUi.text(this,s,size,bold,color)
    private fun gap(n: Int) = OceanUi.gap(this,n)
    private fun button(s: String, primary: Boolean = false, danger: Boolean = false, click: () -> Unit) =
        OceanUi.button(this,s,primary,danger,click)
    private fun row() = OceanUi.row(this)
    private fun full() = LinearLayout.LayoutParams(-1,-2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = OceanUi.background
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        model = PetListModel(PetStore.load(this))
        PetStore.save(this,model.items) // persist migrated identities before service reconciliation
        query = savedInstanceState?.getString("query") ?: ""
        filter = PetFilter.entries.firstOrNull { it.name == savedInstanceState?.getString("filter") } ?: PetFilter.ALL
        selecting = savedInstanceState?.getBoolean("selecting") ?: false
        selected.addAll(savedInstanceState?.getStringArrayList("selected") ?: emptyList())
        selected.retainAll(model.items.map { it.id }.toSet())
        val root = column().apply { setBackgroundColor(OceanUi.background) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        host = FrameLayout(this)
        root.addView(host,LinearLayout.LayoutParams(-1,0,1f))
        oceanPage = buildOceanPage()
        creaturePage = buildCreaturePage()
        settingsPage = buildSettingsPage()
        pages.addAll(listOf(oceanPage,creaturePage,settingsPage))
        pages.forEach { host.addView(it,FrameLayout.LayoutParams(-1,-1)) }
        undoBar = row().apply {
            setPadding(dp(16),dp(6),dp(12),dp(6))
            background = OceanUi.rounded(this@MainActivity,OceanUi.pale,12)
            visibility = View.GONE
        }
        undoText = text("",13f)
        undoBar.addView(undoText,LinearLayout.LayoutParams(0,-2,1f))
        undoBar.addView(button("復原") { undoDeletion() })
        root.addView(undoBar,full())
        val navigation = row().apply {
            setPadding(dp(10),dp(8),dp(10),dp(8)); setBackgroundColor(Color.WHITE)
        }
        listOf("≈\n海洋","◇\n生物","⚙\n設定").forEachIndexed { index, label ->
            val tab = button(label) { showTab(index) }.apply {
                tag = "tab_$index"; textSize = 13f
                contentDescription = listOf("海洋","生物","設定")[index]
                minHeight = dp(58)
            }
            navButtons.add(tab)
            navigation.addView(tab,LinearLayout.LayoutParams(0,dp(62),1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        root.addView(navigation,full())
        setContentView(root)
        showTab((savedInstanceState?.getInt("tab") ?: 0).coerceIn(0,2))
        preview.setSharks(model.items)
        updateSummary()
        restoreUndo(savedInstanceState)
    }

    private fun page(content: LinearLayout): ScrollView = ScrollView(this).apply {
        isFillViewport = true; clipToPadding = false
        addView(content,FrameLayout.LayoutParams(-1,-2))
    }

    private fun card(title: String, subtitle: String? = null): LinearLayout = column(16).apply {
        background = OceanUi.rounded(this@MainActivity,Color.WHITE,18,OceanUi.line)
        elevation = dp(1).toFloat()
        addView(text(title,17f,true))
        if (subtitle != null) { addView(gap(5)); addView(text(subtitle,13f,color=OceanUi.muted)) }
    }

    private fun buildOceanPage(): View {
        val content = column(18)
        content.addView(text("海洋夥伴",28f,true))
        status = text("",14f,color=OceanUi.teal).apply { tag = "desktop_status" }
        content.addView(status)
        content.addView(gap(14))
        val previewBox = FrameLayout(this).apply {
            background = OceanUi.rounded(this@MainActivity,Color.rgb(5,37,55),20)
            clipToOutline = true
        }
        preview = SharkPreviewView(this)
        preview.applyEffectsStrength(EffectsStrength.fromId(prefs.getString(EffectsStrength.PREFERENCE,null)))
        preview.applySocialMode(SocialMode.fromId(prefs.getString(SocialMode.PREFERENCE,null)))
        previewBox.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val previewHeight = (resources.displayMetrics.heightPixels/resources.displayMetrics.density*.35f).toInt().coerceIn(200,330)
        content.addView(previewBox,LinearLayout.LayoutParams(-1,dp(previewHeight)))
        content.addView(gap(12))
        val stats = row()
        total = text("",22f,true)
        stats.addView(total,LinearLayout.LayoutParams(0,-2,1f))
        stats.addView(button("＋ 新增生物",true) { showAddSheet() })
        content.addView(stats)
        content.addView(gap(12))
        startStop = button("啟動桌面游動",true) { toggleDesktop() }.apply { tag = "desktop_toggle" }
        content.addView(startStop,full())
        content.addView(gap(14))
        val social = card("魚群互動")
        socialShortcut = button("") { showSocialPicker() }
        social.addView(gap(8)); social.addView(socialShortcut,full())
        content.addView(social)
        content.addView(gap(12))
        content.addView(text("生物與設定變更會即時套用到預覽，以及游動中的桌面。",12f,color=OceanUi.muted))
        return page(content)
    }

    private fun buildCreaturePage(): View {
        val root = column(18)
        val heading = row()
        val titles = column()
        titles.addView(text("我的生物",27f,true))
        creatureTotal = text("",14f,color=OceanUi.muted)
        titles.addView(creatureTotal)
        heading.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        selectToggle = button("選取") {
            selecting = !selecting
            if (!selecting) selected.clear()
            renderGrid()
        }.apply { tag = "selection_toggle" }
        heading.addView(selectToggle)
        root.addView(heading); root.addView(gap(12))
        val search = EditText(this).apply {
            hint = "搜尋生物"; textSize = 15f; setSingleLine(true)
            setTextColor(OceanUi.ink); setHintTextColor(OceanUi.muted)
            background = OceanUi.rounded(this@MainActivity,OceanUi.pale,14)
            setPadding(dp(14),dp(10),dp(14),dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
            tag = "pet_search"; setText(query)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString() ?: ""; limitSelectionToVisible(); renderGrid()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.addView(search,full()); root.addView(gap(10))
        val chips = row()
        PetFilter.entries.forEach { value ->
            val chip = button(value.label) {
                filter = value; limitSelectionToVisible(); refreshFilters(); renderGrid()
            }.apply { tag = "filter_${value.name}" }
            filterButtons[value] = chip
            chips.addView(chip,LinearLayout.LayoutParams(-2,dp(44)).apply { marginEnd=dp(8) })
        }
        refreshFilters()
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(chips)
        },full())
        root.addView(gap(8))
        selectionBar = column().apply { visibility = View.GONE }
        val selectRow = row()
        selectedCount = text("",14f,true)
        selectRow.addView(selectedCount,LinearLayout.LayoutParams(0,-2,1f))
        selectRow.addView(button("全選此篩選") {
            val ids = model.filtered(query,filter).map { it.id }
            if (ids.all { it in selected }) selected.removeAll(ids.toSet()) else selected.addAll(ids)
            renderGrid()
        })
        selectionBar.addView(selectRow)
        val editRow = row()
        editRow.addView(button("批次調整") {
            if (selected.isEmpty()) toast("請先選取生物") else showEditSheet(selected.toSet())
        },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(8) })
        editRow.addView(button("刪除所選",danger=true) {
            if (selected.isEmpty()) toast("請先選取生物") else confirmDelete(selected.toSet())
        },LinearLayout.LayoutParams(0,dp(48),1f))
        selectionBar.addView(editRow); selectionBar.addView(gap(8))
        root.addView(selectionBar)
        val results = column()
        grid = GridLayout(this).apply { columnCount=2; tag="pet_grid" }
        results.addView(grid,full())
        empty = text("",15f,color=OceanUi.muted).apply {
            gravity=Gravity.CENTER; setPadding(dp(16),dp(40),dp(16),dp(40))
        }
        results.addView(empty,full())
        creatureScroll = ScrollView(this).apply { addView(results); isFillViewport=true }
        root.addView(creatureScroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(gap(8))
        root.addView(button("＋ 新增生物",true) { showAddSheet() },full())
        return root
    }

    private fun limitSelectionToVisible() {
        selected.retainAll(model.filtered(query,filter).map { it.id }.toSet())
    }

    private fun refreshFilters() {
        filterButtons.forEach { (value,view) ->
            view.background = OceanUi.rounded(this,if(filter == value) OceanUi.teal else OceanUi.pale,20)
            view.setTextColor(if(filter == value) Color.WHITE else OceanUi.muted)
        }
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        val visible = model.filtered(query,filter)
        selected.retainAll(model.items.map { it.id }.toSet())
        grid.removeAllViews()
        selectToggle.text = if(selecting) "完成" else "選取"
        selectionBar.visibility = if(selecting) View.VISIBLE else View.GONE
        selectedCount.text = "已選 ${selected.size} 隻"
        creatureTotal.text = "${model.items.size} / ${SharkConfig.MAX_PETS} 隻 · 顯示 ${visible.size} 隻"
        empty.visibility = if(visible.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if(model.items.isEmpty()) "海洋還很安靜，新增幾位夥伴吧。" else "沒有符合搜尋或分類的生物。"
        visible.forEachIndexed { index, pet ->
            val selectedCard = pet.id in selected
            val card = column(10).apply {
                background = OceanUi.rounded(this@MainActivity,if(selectedCard) Color.rgb(216,244,245) else OceanUi.pale,
                    16,if(selectedCard) OceanUi.teal else OceanUi.line)
                isClickable=true; isFocusable=true
                tag="pet_${pet.id}"
                contentDescription="${pet.species.displayName}，大小 ${pet.size}%，游速 ${pet.speed}%"
                setOnClickListener {
                    if(selecting) {
                        if(!selected.add(pet.id)) selected.remove(pet.id)
                        renderGrid()
                    } else showEditSheet(setOf(pet.id))
                }
                setOnLongClickListener {
                    selecting=true; selected.add(pet.id); renderGrid(); true
                }
            }
            card.addView(PetThumbnailView(this,pet.species),LinearLayout.LayoutParams(-1,dp(94)))
            card.addView(text((if(selecting) if(selectedCard) "✓ " else "○ " else "")+pet.species.displayName,15f,true))
            card.addView(gap(3))
            card.addView(text("大小 ${pet.size}% · 游速 ${pet.speed}%",12f,color=OceanUi.muted))
            val params = GridLayout.LayoutParams().apply {
                columnSpec=GridLayout.spec(index%2,1f); rowSpec=GridLayout.spec(index/2)
                width=0; height=-2
                setMargins(if(index%2==0) 0 else dp(5),dp(5),if(index%2==0) dp(5) else 0,dp(5))
            }
            grid.addView(card,params)
        }
        gridDirty=false
    }

    private fun buildSettingsPage(): View {
        val content=column(18)
        content.addView(text("設定",27f,true)); content.addView(text("變更會立即套用",14f,color=OceanUi.muted))
        content.addView(gap(18))
        val visual=card("視覺效果","尾流、水紋與氣泡")
        visual.addView(gap(10))
        val effects=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        val initial=EffectsStrength.fromId(prefs.getString(EffectsStrength.PREFERENCE,null))
        EffectsStrength.entries.forEach { option ->
            effects.addView(RadioButton(this).apply {
                id=View.generateViewId(); text=option.label; tag=option; isChecked=initial==option
                buttonTintList=android.content.res.ColorStateList.valueOf(OceanUi.teal)
            })
        }
        effects.setOnCheckedChangeListener { group,id ->
            val value=group.findViewById<RadioButton>(id)?.tag as? EffectsStrength
            if(value!=null) {
                prefs.edit().putString(EffectsStrength.PREFERENCE,value.id).apply()
                preview.applyEffectsStrength(value); notifyService("EFFECTS_CHANGED")
            }
        }
        visual.addView(effects,full()); content.addView(visual); content.addView(gap(14))
        val interaction=card("魚群互動","輕柔互動，仍允許自然交錯")
        interaction.addView(gap(8))
        val modes=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,SocialMode.entries.map { it.label })
            setSelection(SocialMode.entries.indexOf(SocialMode.fromId(prefs.getString(SocialMode.PREFERENCE,null))))
            tag="social_mode"
        }
        val modeDescription=text("",13f,color=OceanUi.muted)
        modes.onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value=SocialMode.entries[position]
                modeDescription.text=value.description
                if(prefs.getString(SocialMode.PREFERENCE,null)!=value.id) {
                    prefs.edit().putString(SocialMode.PREFERENCE,value.id).apply()
                    preview.applySocialMode(value); notifyService("SOCIAL_CHANGED"); updateSummary()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        interaction.addView(modes,LinearLayout.LayoutParams(-1,dp(48))); interaction.addView(modeDescription)
        content.addView(interaction); content.addView(gap(14))
        val shake=card("搖動互動")
        shakeEnabled=prefs.getBoolean(ShakeSensor.PREFERENCE,false) && shakeSensor.isAvailable
        shake.addView(Switch(this).apply {
            text="搖動驚嚇"; textSize=16f; isChecked=shakeEnabled; isEnabled=shakeSensor.isAvailable
            setPadding(0,dp(8),0,dp(8)); minHeight=dp(48); tag="shake_switch"
            setOnCheckedChangeListener { _, enabled ->
                shakeEnabled=enabled
                prefs.edit().putBoolean(ShakeSensor.PREFERENCE,enabled).apply()
                updatePreviewShake()
                if(!enabled) preview.stopStartle()
                notifyService("SHAKE_CHANGED")
            }
        })
        shake.addView(text(if(shakeSensor.isAvailable) "搖動手機，短暫加速散開；觸發間隔 4 秒。"
            else "此裝置沒有加速度感測器。",13f,color=OceanUi.muted))
        content.addView(shake); content.addView(gap(14))
        content.addView(card("熄屏時暫停","動畫與桌面搖動偵測會在熄屏時暫停，亮屏後恢復。"))
        return page(content)
    }

    private fun showTab(index: Int) {
        currentTab=index
        pages.forEachIndexed { i,page -> page.visibility=if(i==index) View.VISIBLE else View.GONE }
        navButtons.forEachIndexed { i,tab ->
            tab.background=OceanUi.rounded(this,if(i==index) OceanUi.pale else Color.WHITE,14)
            tab.setTextColor(if(i==index) OceanUi.teal else OceanUi.muted)
            tab.isSelected=i==index
        }
        if(index==1 && gridDirty) renderGrid()
        if(index==0) preview.resumePreview()
        updatePreviewShake()
    }

    private fun updatePreviewShake() {
        if(shakeEnabled && currentTab==0 && hasWindowFocus()) shakeSensor.start() else shakeSensor.stop()
    }

    private fun updateSummary() {
        if(!::total.isInitialized) return
        val active=SharkService.isActive
        total.text="${model.items.size} / ${SharkConfig.MAX_PETS} 隻"
        status.text=if(active) "● 桌面游動中" else "○ 桌面尚未啟動"
        status.setTextColor(if(active) OceanUi.teal else OceanUi.muted)
        startStop.text=if(active) "■ 停止桌面游動" else "▶ 啟動桌面游動"
        val mode=SocialMode.fromId(prefs.getString(SocialMode.PREFERENCE,null))
        socialShortcut.text="${mode.label}  ›\n${mode.description}"
        if(::creatureTotal.isInitialized && currentTab!=1) creatureTotal.text="${model.items.size} / ${SharkConfig.MAX_PETS} 隻"
    }

    private fun toggleDesktop() {
        commitChanges()
        if(SharkService.isActive) {
            undoWasRunning=false
            startService(Intent(this,SharkService::class.java).setAction("STOP"))
        } else if(model.items.isEmpty()) {
            toast("先新增一位海洋夥伴吧"); showAddSheet()
        } else if(!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            toast("允許顯示在其他應用程式上層後，再按啟動")
        } else startForegroundService(Intent(this,SharkService::class.java).setAction("START"))
    }

    private fun notifyService(action: String) { sendBroadcast(Intent("$packageName.$action").setPackage(packageName)) }

    private fun dataChanged(immediate: Boolean = true) {
        gridDirty=true; dataDirty=true
        if(immediate) commitChanges() else if(!commitQueued) {
            commitQueued=true; main.postDelayed(commit,60)
        }
    }

    private fun commitChanges() {
        if(!::model.isInitialized || !::preview.isInitialized) return
        main.removeCallbacks(commit); commitQueued=false
        if (!dataDirty) return
        dataDirty=false
        PetStore.save(this,model.items)
        preview.setSharks(model.items)
        notifyService("PETS_CHANGED")
        updateSummary()
        if(currentTab==1 && gridDirty) renderGrid()
    }

    private fun showSocialPicker() {
        val selected=SocialMode.entries.indexOf(SocialMode.fromId(prefs.getString(SocialMode.PREFERENCE,null)))
        AlertDialog.Builder(this).setTitle("魚群互動")
            .setSingleChoiceItems(SocialMode.entries.map { "${it.label}：${it.description}" }.toTypedArray(),selected) { dialog,which ->
                val mode=SocialMode.entries[which]
                prefs.edit().putString(SocialMode.PREFERENCE,mode.id).apply()
                preview.applySocialMode(mode); notifyService("SOCIAL_CHANGED"); updateSummary()
                // Keep the settings spinner synchronized without rebuilding the scene.
                settingsPage.findViewWithTag<Spinner>("social_mode")?.setSelection(which)
                dialog.dismiss()
            }.setNegativeButton("取消",null).show()
    }

    private fun showAddSheet() {
        val available=RandomPetBatch.remainingSlots(model.items.size)
        if(available==0) { toast("已達 ${SharkConfig.MAX_PETS} 隻上限"); return }
        val content=column()
        content.addView(text("目前 ${model.items.size} 隻，還能新增 $available 隻。",14f,color=OceanUi.muted))
        content.addView(gap(12)); content.addView(text("物種",15f,true))
        val species=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,
                listOf("隨機物種（${SharkSpecies.entries.size} 種，可重複）")+SharkSpecies.entries.map { it.displayName })
            tag="add_species"
        }
        content.addView(species,LinearLayout.LayoutParams(-1,dp(48)))
        content.addView(text("新增數量",15f,true))
        val quantity=EditText(this).apply {
            inputType=InputType.TYPE_CLASS_NUMBER; setSingleLine(true)
            setText(minOf(5,available).toString()); selectAll(); hint="1～$available"; tag="add_quantity"
        }
        content.addView(quantity,full()); content.addView(gap(10))
        content.addView(text("大小與速度",15f,true))
        var variation=BatchVariation.fromId(prefs.getString(BatchVariation.PREFERENCE,null))
        val choices=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
        BatchVariation.entries.forEach { value -> choices.addView(RadioButton(this).apply {
            id=View.generateViewId(); text=value.label; tag=value; isChecked=value==variation; textSize=14f
        }) }
        choices.setOnCheckedChangeListener { group,id ->
            (group.findViewById<RadioButton>(id)?.tag as? BatchVariation)?.let { variation=it }
        }
        content.addView(choices)
        content.addView(text("常態分佈以 100% 為中心：大小標準差 25%、速度 20%，各自抽樣，超出範圍重抽。",12f,color=OceanUi.muted))
        val footer=row()
        val dialog=createSheet("新增生物",content,footer)
        footer.addView(button("取消") { dialog.dismiss() },LinearLayout.LayoutParams(0,dp(50),1f).apply { marginEnd=dp(10) })
        footer.addView(button("新增",true) {
            val n=quantity.text.toString().trim().toIntOrNull()
            val slots=RandomPetBatch.remainingSlots(model.items.size)
            if(n==null || n !in 1..slots) { quantity.error="請輸入 1～$slots 的整數"; return@button }
            val picked=if(species.selectedItemPosition==0) null else SharkSpecies.entries[species.selectedItemPosition-1]
            model.add(RandomPetBatch.generate(n,model.items.size,variation=variation,species=picked))
            prefs.edit().putString(BatchVariation.PREFERENCE,variation.id).apply()
            query=""; filter=PetFilter.ALL; selected.clear(); selecting=false
            dataChanged(); dialog.dismiss()
            showAddedMessage(n)
        },LinearLayout.LayoutParams(0,dp(50),1f))
        dialog.show(); finishSheetWindow(dialog)
    }

    private fun showAddedMessage(n: Int) {
        // A direct action replaces the need to find the newly added cards manually.
        toast("已新增 $n 隻，已套用到預覽與游動中的桌面")
        showTab(1); renderGrid()
        refreshFilters()
        creaturePage.findViewWithTag<EditText>("pet_search")?.setText("")
        creatureScroll.post { creatureScroll.smoothScrollTo(0,grid.bottom) }
    }

    private fun showEditSheet(ids: Set<String>) {
        val pets=model.items.filter { it.id in ids }
        if(pets.isEmpty()) return
        val single=pets.size==1
        val content=column()
        if(single) content.addView(PetThumbnailView(this,pets.first().species),LinearLayout.LayoutParams(-1,dp(120)))
        else content.addView(text("拖曳哪一項，就只統一調整該項；其他設定維持原值。",13f,color=OceanUi.muted))
        fun slider(title: String, minimum: Int, maximum: Int, values: List<Int>, edit: (Int)->Unit) {
            val uniform=values.distinct().size==1
            val label=text("$title：${if(uniform) "${values.first()}%" else "各自不同"}",17f,true)
            content.addView(gap(12)); content.addView(label)
            val bar=SeekBar(this).apply {
                max=maximum-minimum; progress=(if(uniform) values.first() else 100)-minimum
                contentDescription=title; tag=if(title=="大小") "edit_size" else "edit_speed"
                minHeight=dp(48); OceanUi.tintSeekBar(this)
            }
            bar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if(fromUser) { val value=minimum+progress; label.text="$title：$value%"; edit(value); dataChanged(false) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { commitChanges() }
            })
            content.addView(bar,full()); content.addView(text("$minimum–$maximum%",12f,color=OceanUi.muted))
        }
        slider("大小",30,200,pets.map { it.size }) { model.update(ids,size=it) }
        slider("游速",20,200,pets.map { it.speed }) { model.update(ids,speed=it) }
        content.addView(gap(14)); content.addView(text("調整後立即套用",13f,color=OceanUi.muted))
        val footer=row()
        val title=if(single) "${pets.first().species.displayName} ${model.items.indexOfFirst { it.id==pets.first().id }+1}" else "調整 ${pets.size} 隻生物"
        val dialog=createSheet(title,content,footer)
        footer.addView(button(if(single) "刪除此生物" else "刪除所選",danger=true) {
            dialog.dismiss(); confirmDelete(ids)
        },LinearLayout.LayoutParams(0,dp(50),1f).apply { marginEnd=dp(10) })
        footer.addView(button("完成",true) { commitChanges(); dialog.dismiss() },LinearLayout.LayoutParams(0,dp(50),1f))
        dialog.show(); finishSheetWindow(dialog)
    }

    private fun confirmDelete(ids: Set<String>) {
        val count=model.items.count { it.id in ids }
        if(count==0) return
        if(count==1) deletePets(ids) else AlertDialog.Builder(this)
            .setTitle("刪除 $count 隻生物？").setMessage("刪除後可在下方短暫復原。")
            .setNegativeButton("取消",null).setPositiveButton("刪除") { _,_ -> deletePets(ids) }.show()
    }

    private fun deletePets(ids: Set<String>) {
        undoWasRunning=SharkService.isActive
        removed=model.remove(ids)
        selected.removeAll(ids)
        dataChanged()
        undoDeadline=SystemClock.uptimeMillis()+10_000
        showUndo()
    }

    private fun showUndo() {
        if(removed.isEmpty()) return
        undoText.text="已刪除 ${removed.size} 隻"
        undoBar.visibility=View.VISIBLE
        main.removeCallbacks(expireUndo)
        main.postDelayed(expireUndo,(undoDeadline-SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    private fun undoDeletion() {
        if(removed.size>RandomPetBatch.remainingSlots(model.items.size)) {
            toast("需保留 ${removed.size} 個名額才能全部復原"); return
        }
        val n=model.restore(removed)
        removed=emptyList(); undoBar.visibility=View.GONE; main.removeCallbacks(expireUndo)
        dataChanged()
        if(undoWasRunning && !SharkService.isActive && Settings.canDrawOverlays(this))
            startForegroundService(Intent(this,SharkService::class.java).setAction("START"))
        toast("已復原 $n 隻")
    }

    private fun createSheet(title: String, content: View, footer: View): Dialog {
        sheet?.dismiss()
        val dialog=Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel=column(20).apply { background=OceanUi.rounded(this@MainActivity,Color.WHITE,24) }
        val handle=DragHandle(this,dialog,panel)
        panel.addView(handle,LinearLayout.LayoutParams(dp(48),dp(20)).apply { gravity=Gravity.CENTER_HORIZONTAL })
        val heading=row()
        heading.addView(text(title,22f,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(button("✕") { dialog.dismiss() }.apply { contentDescription="關閉" },LinearLayout.LayoutParams(dp(48),dp(48)))
        panel.addView(heading); panel.addView(gap(8))
        val scroll=LimitedScrollView(this,(resources.displayMetrics.heightPixels*.57f).toInt())
        scroll.addView(content)
        panel.addView(scroll,full()); panel.addView(gap(16)); panel.addView(footer,full())
        dialog.setContentView(panel)
        dialog.setOnDismissListener { commitChanges(); if(sheet==dialog) sheet=null }
        sheet=dialog
        return dialog
    }

    private fun finishSheetWindow(dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM); setLayout(-1,-2)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=.35f }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private class LimitedScrollView(context: Context, private val limit: Int): ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available=MeasureSpec.getSize(heightMeasureSpec)
            val maximum=if(available>0) minOf(limit,available) else limit
            super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST))
        }
    }

    private class DragHandle(context: Context, val dialog: Dialog, val panel: View): View(context) {
        private var down=0f
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=OceanUi.muted }
        init { contentDescription="向下滑動關閉面板"; isClickable=true; setOnClickListener { dialog.dismiss() } }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val h=4f*resources.displayMetrics.density
            canvas.drawRoundRect(0f,height/2f-h/2,width.toFloat(),height/2f+h/2,h,h,paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> down=event.rawY
                MotionEvent.ACTION_MOVE -> panel.translationY=(event.rawY-down).coerceAtLeast(0f)
                MotionEvent.ACTION_UP -> {
                    if(panel.translationY>64*resources.displayMetrics.density) dialog.dismiss()
                    else if(kotlin.math.abs(event.rawY-down)<8*resources.displayMetrics.density) performClick()
                    else panel.animate().translationY(0f).setDuration(150).start()
                }
                MotionEvent.ACTION_CANCEL -> panel.animate().translationY(0f).setDuration(150).start()
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    private fun toast(message: String) { Toast.makeText(this,message,Toast.LENGTH_SHORT).show() }

    override fun onStart() {
        super.onStart()
        if(Build.VERSION.SDK_INT>=33) registerReceiver(stateReceiver,IntentFilter("$packageName.STATE_CHANGED"),Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(stateReceiver,IntentFilter("$packageName.STATE_CHANGED"))
        receiverRegistered=true
    }
    override fun onResume() { super.onResume(); updateSummary(); updatePreviewShake() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if(::preview.isInitialized) updatePreviewShake() }
    override fun onPause() { commitChanges(); shakeSensor.stop(); super.onPause() }
    override fun onStop() { if(receiverRegistered) { unregisterReceiver(stateReceiver); receiverRegistered=false }; super.onStop() }
    override fun onDestroy() { sheet?.dismiss(); main.removeCallbacksAndMessages(null); shakeSensor.stop(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        commitChanges()
        outState.putInt("tab",currentTab); outState.putString("query",query); outState.putString("filter",filter.name)
        outState.putBoolean("selecting",selecting); outState.putStringArrayList("selected",ArrayList(selected))
        if(removed.isNotEmpty()) {
            val json=JSONArray()
            removed.forEach { e -> json.put(JSONObject().put("index",e.index).put("id",e.config.id)
                .put("size",e.config.size).put("speed",e.config.speed).put("species",e.config.species.id)) }
            outState.putString("undo",json.toString()); outState.putLong("undo_deadline",undoDeadline)
            outState.putBoolean("undo_running",undoWasRunning)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreUndo(state: Bundle?) {
        if(state==null || state.getLong("undo_deadline")<=SystemClock.uptimeMillis()) return
        try {
            val json=JSONArray(state.getString("undo") ?: "[]")
            removed=(0 until json.length()).map { i ->
                val o=json.getJSONObject(i)
                RemovedPet(o.getInt("index"),SharkConfig.fromSaved(o.getInt("size"),o.getInt("speed"),o.getString("species"),o.getString("id")))
            }
            undoDeadline=state.getLong("undo_deadline"); undoWasRunning=state.getBoolean("undo_running")
            showUndo()
        } catch (_: Exception) { removed=emptyList() }
    }
}
