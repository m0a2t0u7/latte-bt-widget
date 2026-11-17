package com.shinyalatte.btwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
                for (provider in listOf(BtWidgetSmall(), BtWidgetMedium(), BtWidgetWide(), BtWidgetProvider())) {
                    for (state in Ui.State.values()) {
                        val snap = Ui.Snap(state, if (state == Ui.State.CONNECTED) listOf("UGREEN FitBuds") else emptyList(), "UGREEN FitBuds")
                        val width = when (provider.kind) {
                            BtWidgetProvider.Kind.SMALL -> 90
                            BtWidgetProvider.Kind.MEDIUM -> 180
                            BtWidgetProvider.Kind.WIDE -> 270
                            BtWidgetProvider.Kind.LARGE -> 360
                        }
                        val view = provider.buildForSize(c, snap, width.toFloat(), 48f).apply(c, null)
                        measureWidget(view, width, 48)
                        val status = view.findViewById<TextView>(R.id.status)
                        assertTrue(status.text.isNotEmpty())
                        val colored = state in listOf(Ui.State.CONNECTED, Ui.State.IDLE, Ui.State.OFF)
                        val fill = solidColor(view)
                        if (colored) {
                            assertEquals(if (state == Ui.State.CONNECTED) CONNECTED_GREEN else DISCONNECTED_RED, fill)
                            assertEquals(Color.WHITE, status.currentTextColor)
                            assertEquals(Color.WHITE, view.findViewById<TextView>(R.id.device).currentTextColor)
                        } else {
                            assertTrue(fill != CONNECTED_GREEN && fill != DISCONNECTED_RED)
                        }
                        val button = view.findViewById<TextView>(R.id.btn_main)
                        when (state) {
                            Ui.State.CONNECTED, Ui.State.IDLE -> {
                                val connected = state == Ui.State.CONNECTED
                                assertEquals(View.VISIBLE, button.visibility)
                                assertEquals(if (connected) "切断" else "再接続", button.text.toString())
                                assertEquals(Color.parseColor("#102A43"), button.currentTextColor)
                                assertEquals(Color.WHITE, solidColor(button))
                                assertTrue(button.top >= 0 && button.bottom <= view.height)
                                assertEquals(0, button.layout.getEllipsisCount(0))
                            }
                            else -> assertEquals(View.GONE, button.visibility)
                        }
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
        for (provider in listOf(BtWidgetSmall(), BtWidgetMedium(), BtWidgetWide(), BtWidgetProvider())) {
            val connected = provider.build(c, Ui.Snap(Ui.State.CONNECTED, listOf("UGREEN FitBuds"), "UGREEN FitBuds"))
            val disconnected = provider.build(c, Ui.Snap(Ui.State.IDLE, emptyList(), "UGREEN FitBuds"))
            val neutral = provider.build(c, Ui.Snap(Ui.State.NOPERM, emptyList(), null))
            for (night in listOf(false, true)) {
                val config = Configuration(c.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = c.createConfigurationContext(config)
                instrumentation.runOnMainSync {
                    val view = connected.apply(themed, null)
                    val device = view.findViewById<TextView>(R.id.device)
                    val icon = view.findViewById<android.widget.ImageView>(R.id.icon)
                    assertEquals(CONNECTED_GREEN, solidColor(view))
                    assertEquals(Color.WHITE, device.currentTextColor)
                    if (icon != null) assertEquals(Color.WHITE, icon.imageTintList!!.defaultColor)

                    disconnected.reapply(themed, view)
                    assertEquals(DISCONNECTED_RED, solidColor(view))
                    assertEquals(Color.WHITE, device.currentTextColor)

                    neutral.reapply(themed, view)
                    assertEquals(Color.parseColor(if (night) "#E6202326" else "#FFFFFF"), solidColor(view))
                    assertEquals(themed.getColor(R.color.widget_text), device.currentTextColor)
                    if (icon != null) assertEquals(themed.getColor(R.color.widget_muted), icon.imageTintList!!.defaultColor)

                    connected.reapply(themed, view)
                    assertEquals(CONNECTED_GREEN, solidColor(view))
                    assertEquals(Color.WHITE, device.currentTextColor)
                }
            }
        }
        testResizing(c)
        assertEquals(before, Bt.notifEnabled(c))
        Appearance.save(c, Appearance.SYSTEM)
    }

    private fun solidColor(view: View): Int? =
        (view.background as GradientDrawable).color?.defaultColor

    private companion object {
        val CONNECTED_GREEN = Color.parseColor("#08764F")
        val DISCONNECTED_RED = Color.parseColor("#B62B3B")
    }

    private fun testResizing(c: Context) {
        val snap = Ui.Snap(Ui.State.CONNECTED, listOf("UGREEN FitBuds"), "UGREEN FitBuds")
        val provider = BtWidgetWide()
        val sizes = listOf(40 to 40, 55 to 110, 160 to 40, 270 to 39, 270 to 40, 270 to 48)
        instrumentation.runOnMainSync {
            for ((width, height) in sizes) {
                val view = provider.buildForSize(c, snap, width.toFloat(), height.toFloat()).apply(c, null)
                measureWidget(view, width, height)
                val device = view.findViewById<TextView>(R.id.device)
                assertEquals(if (width == 40) View.GONE else View.VISIBLE, device.visibility)
                assertEquals(width >= 220 && height >= 40, view.findViewById<View>(R.id.btn_switch) != null)
                val button = view.findViewById<TextView>(R.id.btn_main)
                assertTrue(button.top >= 0 && button.bottom <= view.height)
                render(view, "widget-resize-${width}x$height")
            }

            val bounds = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 55)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 270)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48)
            }
            val views = provider.buildForWidget(c, snap, bounds)
            val portrait = c.createConfigurationContext(Configuration(c.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
            })
            val landscape = c.createConfigurationContext(Configuration(c.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            })
            assertEquals(View.VISIBLE, views.apply(portrait, null).findViewById<View>(R.id.device).visibility)
            assertEquals(null, views.apply(portrait, null).findViewById<View>(R.id.btn_switch))
            assertTrue(views.apply(landscape, null).findViewById<View>(R.id.btn_switch) != null)
        }
    }

    private fun measureWidget(view: View, width: Int, height: Int) {
        val density = view.resources.displayMetrics.density
        view.measure(
            View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun render(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File(instrumentation.targetContext.filesDir, "appearance-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
