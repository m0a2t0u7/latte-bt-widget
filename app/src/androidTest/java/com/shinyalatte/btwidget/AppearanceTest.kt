package com.shinyalatte.btwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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
                        val tint = when (state) {
                            Ui.State.CONNECTED -> Tint.GREEN
                            Ui.State.IDLE, Ui.State.OFF -> Tint.RED
                            else -> if (Appearance.light(c)) Tint.LIGHT else Tint.DARK
                        }
                        assertGlass(view, tint)
                        assertEquals(DARK_INK, status.currentTextColor)
                        assertEquals(null, status.background)
                        val device = view.findViewById<TextView>(R.id.device)
                        assertEquals(DARK_INK, device.currentTextColor)
                        assertEquals(null, device.background)
                        val button = view.findViewById<TextView>(R.id.btn_main)
                        when (state) {
                            Ui.State.CONNECTED, Ui.State.IDLE -> {
                                val connected = state == Ui.State.CONNECTED
                                assertEquals(View.VISIBLE, button.visibility)
                                assertEquals(if (connected) "切断" else "再接続", button.text.toString())
                                assertEquals(DARK_INK, button.currentTextColor)
                                assertGlassButton(button)
                                if (!connected) {
                                    val lavender = sampleGradient(button).last()
                                    assertTrue(Color.blue(lavender) > Color.red(lavender))
                                    assertTrue(Color.red(lavender) > Color.green(lavender))
                                }
                                assertTrue(button.top >= 0 && button.bottom <= view.height)
                                assertEquals(0, button.layout.getEllipsisCount(0))
                            }
                            else -> assertEquals(View.GONE, button.visibility)
                        }
                        val switch = view.findViewById<TextView>(R.id.btn_switch)
                        if (switch != null && switch.visibility == View.VISIBLE) {
                            assertGlassButton(switch)
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
                    assertGlass(view, Tint.GREEN)
                    assertEquals(DARK_INK, device.currentTextColor)

                    disconnected.reapply(themed, view)
                    assertGlass(view, Tint.RED)
                    assertEquals(DARK_INK, device.currentTextColor)

                    neutral.reapply(themed, view)
                    assertGlass(view, if (night) Tint.DARK else Tint.LIGHT)
                    assertEquals(DARK_INK, device.currentTextColor)

                    connected.reapply(themed, view)
                    assertGlass(view, Tint.GREEN)
                    assertEquals(DARK_INK, device.currentTextColor)
                }
            }
        }
        testResizing(c)
        assertEquals(before, Bt.notifEnabled(c))
        Appearance.save(c, Appearance.SYSTEM)
    }

    private val DARK_INK = Color.parseColor("#17273F")

    private enum class Tint { GREEN, RED, LIGHT, DARK }

    private fun assertGlass(view: View, tint: Tint) {
        for (pixel in sampleGradient(view)) {
            assertTrue(Color.alpha(pixel) in 160..211)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            assertTrue(when (tint) {
                Tint.GREEN -> green > blue && blue > red
                Tint.RED -> red > blue && blue > green
                Tint.LIGHT -> red > 190 && green >= red && blue >= green
                Tint.DARK -> red > 180 && green > red && blue > green
            })
        }
    }

    private fun assertGlassButton(view: View) {
        for (pixel in sampleGradient(view)) {
            assertTrue(Color.alpha(pixel) in 165..246)
        }
    }

    private fun sampleGradient(view: View): List<Int> =
        sampleBackground(view, 96, 96, listOf(16, 80)).also { assertTrue(it[0] != it[1]) }

    private fun sampleBackground(view: View, width: Int, height: Int, sampleRows: List<Int>): List<Int> {
        val background = view.background
        val originalBounds = Rect(background.bounds)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            background.setBounds(0, 0, bitmap.width, bitmap.height)
            background.draw(Canvas(bitmap))
            return sampleRows.map { row -> bitmap.getPixel(width / 2, row) }
        } finally {
            background.bounds = originalBounds
            bitmap.recycle()
        }
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
