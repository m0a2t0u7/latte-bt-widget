package com.shinyalatte.btwidget

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.io.File

class AppearanceInstrumentation : Instrumentation() {
    private val instrumentation get() = this
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            testThemesAndAllWidgetSizes()
            result.putString("stream", "\nOK (1 test)\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "\nFAILURES!!!\n" + e.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun assertTrue(value: Boolean) { check(value) }
    private fun assertEquals(expected: Any?, actual: Any?) { check(expected == actual) { "Expected $expected but got $actual" } }

    fun testThemesAndAllWidgetSizes() {
        val c = instrumentation.targetContext
        val before = Bt.notifEnabled(c)
        for (mode in listOf(Appearance.LIGHT, Appearance.DARK, Appearance.SYSTEM)) {
            Appearance.save(c, mode)
            assertEquals(mode, Appearance.mode(c))
            val a = instrumentation.startActivitySync(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(a.window.decorView.width > 0)
                render(a.window.decorView, "main-$mode")
                for (provider in listOf(BtWidgetSmall(), BtWidgetMedium(), BtWidgetProvider())) {
                    for (state in Ui.State.values()) {
                        val snap = Ui.Snap(state, if (state == Ui.State.CONNECTED) listOf("UGREEN FitBuds") else emptyList(), "UGREEN FitBuds")
                        val view = provider.build(c, snap).apply(c, null)
                        val width = when (provider.kind) { BtWidgetProvider.Kind.SMALL -> 90; BtWidgetProvider.Kind.MEDIUM -> 180; else -> 360 }
                        val density = c.resources.displayMetrics.density
                        view.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((88 * density).toInt(), View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                        assertTrue(view.findViewById<TextView>(R.id.device).text.isNotEmpty())
                        render(view, "widget-$mode-${provider.kind}-$state")
                    }
                }
                a.finish()
            }
            val picker = instrumentation.startActivitySync(Intent(c, PickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { if (!picker.isFinishing) render(picker.window.decorView, "picker-$mode"); picker.finish() }
        }
        Appearance.save(c, Appearance.SYSTEM)
        for (provider in listOf(BtWidgetSmall(), BtWidgetMedium(), BtWidgetProvider())) {
            val snap = Ui.Snap(Ui.State.CONNECTED, listOf("UGREEN FitBuds"), "UGREEN FitBuds")
            val remote = provider.build(c, snap)
            for (night in listOf(false, true)) {
                val config = Configuration(c.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = c.createConfigurationContext(config)
                instrumentation.runOnMainSync {
                    val view = remote.apply(themed, null)
                    assertEquals(themed.getColor(R.color.widget_text), view.findViewById<TextView>(R.id.device).currentTextColor)
                    val tint = view.findViewById<android.widget.ImageView>(R.id.icon).imageTintList
                    assertEquals(themed.getColor(R.color.widget_connected), tint!!.defaultColor)
                    remote.reapply(themed, view)
                }
            }
        }
        assertEquals(before, Bt.notifEnabled(c))
        Appearance.save(c, Appearance.SYSTEM)
    }

    private fun render(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File(instrumentation.targetContext.filesDir, "appearance-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
