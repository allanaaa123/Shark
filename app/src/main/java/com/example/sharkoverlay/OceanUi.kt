package com.example.sharkoverlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object OceanUi {
    val background = Color.rgb(245,249,252)
    val ink = Color.rgb(15,42,64)
    val muted = Color.rgb(100,124,145)
    val teal = Color.rgb(0,132,151)
    val line = Color.rgb(218,231,239)
    val pale = Color.rgb(231,241,247)
    val red = Color.rgb(204,65,72)
    fun dp(context: Context, n: Int) = (n*context.resources.displayMetrics.density).toInt()
    fun rounded(context: Context, color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(context,radius).toFloat()
            if (stroke != null) setStroke(dp(context,1),stroke)
        }
    fun text(context: Context, value: String, size: Float = 14f, bold: Boolean = false,
             color: Int = ink): TextView = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null,Typeface.BOLD)
    }
    fun column(context: Context, padding: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(context,padding); setPadding(p,p,p,p)
    }
    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    fun button(context: Context, title: String, primary: Boolean = false, danger: Boolean = false,
               onClick: () -> Unit): Button = Button(context).apply {
        text = title; isAllCaps = false; textSize = 14f
        minHeight = dp(context,48); minimumHeight = dp(context,48)
        setPadding(dp(context,14),dp(context,8),dp(context,14),dp(context,8))
        backgroundTintList = null
        background = rounded(context,if(primary) teal else Color.WHITE,14,if(primary) null else if(danger) red else line)
        setTextColor(if(primary) Color.WHITE else if(danger) red else ink)
        setOnClickListener { onClick() }
    }
    fun gap(context: Context, height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1,dp(context,height))
    }
    fun tintSeekBar(bar: SeekBar) {
        bar.progressTintList = ColorStateList.valueOf(teal)
        bar.thumbTintList = ColorStateList.valueOf(teal)
        bar.progressBackgroundTintList = ColorStateList.valueOf(line)
    }
}
