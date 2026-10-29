package com.example.sharkoverlay

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityUiTest {
    private val context: Context get()=RuntimeEnvironment.getApplication()
    private val fixtures get()=listOf(
        SharkConfig(100,100,SharkSpecies.TIGER),SharkConfig(90,80,SharkSpecies.BLUE_SPOTTED_RAY),
        SharkConfig(150,70,SharkSpecies.WHALE_SHARK),SharkConfig(100,110,SharkSpecies.HAMMERHEAD),
        SharkConfig(120,80,SharkSpecies.MANTA_RAY),SharkConfig(100,100,SharkSpecies.GOLDEN_RAY))

    @Before fun setup() {
        context.getSharedPreferences("shark",Context.MODE_PRIVATE).edit().clear().commit()
        PetStore.save(context,fixtures)
    }

    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(root: View,title: String) = views(root).filterIsInstance<Button>().first { it.text.toString()==title }
    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1702,View.MeasureSpec.EXACTLY))
        root.layout(0,0,786,1702)
    }
    private fun idle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
    private fun dialogRoot(): View = ShadowDialog.getLatestDialog().window!!.decorView

    @Test fun tabsFilteringSelectionAndRayCardsWork() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get(); val root=activity.window.decorView
        layout(root); idle()
        root.findViewWithTag<Button>("tab_1").performClick(); layout(root)
        root.findViewWithTag<Button>("filter_RAYS").performClick(); layout(root)
        val grid=root.findViewWithTag<GridLayout>("pet_grid")
        assertEquals(3,grid.childCount)
        assertTrue(grid.getChildAt(0).width>0)
        assertTrue(grid.getChildAt(1).left>=grid.getChildAt(0).right)
        root.findViewWithTag<EditText>("pet_search").setText("蝠鱝")
        assertEquals(1,grid.childCount)
        grid.getChildAt(0).performLongClick()
        assertTrue(views(root).filterIsInstance<TextView>().any { it.text.toString()=="已選 1 隻" })
        root.findViewWithTag<Button>("tab_2").performClick()
        assertTrue(root.findViewWithTag<Spinner>("social_mode").isShown)
        controller.pause().stop().destroy()
    }

    @Test fun addingSpecifiedRaysPersistsWithoutReplacingExistingIds() {
        val before=PetStore.load(context)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val root=controller.get().window.decorView; layout(root); idle()
        button(root,"＋ 新增生物").performClick()
        val dialog=dialogRoot(); layout(dialog)
        dialog.findViewWithTag<Spinner>("add_species").setSelection(SharkSpecies.entries.indexOf(SharkSpecies.MANTA_RAY)+1)
        dialog.findViewWithTag<EditText>("add_quantity").setText("3")
        button(dialog,"新增").performClick(); idle()
        val after=PetStore.load(context)
        assertEquals(before.size+3,after.size)
        assertEquals(before.map { it.id },after.take(before.size).map { it.id })
        assertTrue(after.takeLast(3).all { it.species==SharkSpecies.MANTA_RAY })
        controller.pause().stop().destroy()
    }

    @Test fun deletingOneCardCanBeUndoneInOriginalOrder() {
        val before=PetStore.load(context)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val root=controller.get().window.decorView; layout(root); idle()
        root.findViewWithTag<Button>("tab_1").performClick(); layout(root)
        root.findViewWithTag<View>("pet_${before[1].id}").performClick()
        button(dialogRoot(),"刪除此生物").performClick(); idle()
        assertEquals(before.size-1,PetStore.load(context).size)
        button(root,"復原").performClick(); idle()
        assertEquals(before.map { it.id },PetStore.load(context).map { it.id })
        controller.pause().stop().destroy()
    }

    @Test fun legacySavedSettingsGainStableIdsAndKeepValues() {
        context.getSharedPreferences("shark",Context.MODE_PRIVATE).edit()
            .putString("sharks","[{\"size\":150,\"speed\":75,\"species\":\"whale_shark\"}]").commit()
        val first=PetStore.load(context); val second=PetStore.load(context)
        assertEquals(first[0].id,second[0].id)
        assertEquals(150,second[0].size); assertEquals(75,second[0].speed)
    }

    @Test fun bulkSizeGestureKeepsIndependentSpeedsAndSurvivesRecreation() {
        val before=PetStore.load(context)
        var controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        var root=controller.get().window.decorView; layout(root); idle()
        root.findViewWithTag<Button>("tab_1").performClick(); layout(root)
        root.findViewWithTag<View>("pet_${before[0].id}").performLongClick()
        root.findViewWithTag<View>("pet_${before[1].id}").performClick()
        button(root,"批次調整").performClick()
        val dialog=dialogRoot(); layout(dialog)
        val bar=dialog.findViewWithTag<SeekBar>("edit_size")
        assertTrue(bar.width>0)
        val now=android.os.SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(now,now+20,action,bar.width*.75f,bar.height*.5f,0)
            bar.dispatchTouchEvent(event); event.recycle()
        }
        button(dialog,"完成").performClick(); idle()
        val after=PetStore.load(context)
        assertTrue(after[0].size!=before[0].size)
        assertEquals(after[0].size,after[1].size)
        assertEquals(before.map { it.speed },after.map { it.speed })
        assertEquals(before.drop(2),after.drop(2))
        controller.recreate(); root=controller.get().window.decorView; layout(root); idle()
        assertTrue(root.findViewWithTag<GridLayout>("pet_grid").isShown)
        assertEquals(after,PetStore.load(context))
        controller.pause().stop().destroy()
    }

    @Test fun allFourLayoutStatesRenderWithAllFourteenSpeciesLoaded() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get(); val root=activity.window.decorView; layout(root)
        val ready=AtomicBoolean(false)
        val observer: () -> Unit = { ready.set(SharkSpecies.entries.all { SharkArt.clip(it,SharkMotion.SWIM)!=null }) }
        SharkArt.observe(activity,observer)
        repeat(800) {
            if(!ready.get()) { idle(); Thread.sleep(10) }
        }
        assertTrue("All 14 species must decode, including all five ray assets",ready.get())
        SharkArt.removeObserver(observer)
        val folder=File("build/reports/ui-preview").apply { mkdirs() }
        fun screenshot(name: String,view: View=root) {
            layout(view); idle()
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        screenshot("01-ocean")
        root.findViewWithTag<Button>("tab_1").performClick(); screenshot("02-creatures")
        root.findViewWithTag<Button>("tab_2").performClick(); screenshot("03-settings")
        root.findViewWithTag<Button>("tab_1").performClick(); layout(root)
        val pet=PetStore.load(context).first()
        root.findViewWithTag<View>("pet_${pet.id}").performClick()
        screenshot("04-edit-sheet",dialogRoot())
        ShadowDialog.getLatestDialog().dismiss()
        controller.pause().stop().destroy()
    }
}
