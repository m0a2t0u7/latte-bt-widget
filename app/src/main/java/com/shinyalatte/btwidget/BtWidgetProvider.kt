package com.shinyalatte.btwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.shinyalatte.btwidget.Ui.State

/** 4×1（横長） */
open class BtWidgetProvider : AppWidgetProvider() {
    open val kind = Kind.LARGE
    enum class Kind { SMALL, MEDIUM, WIDE, LARGE }

    internal enum class Layout(val resource: Int) {
        COMPACT(R.layout.widget_small),
        MEDIUM(R.layout.widget_medium),
        FULL(R.layout.widget_bt)
    }

    private val defaultLayout: Layout
        get() = when (kind) {
            Kind.SMALL -> Layout.COMPACT
            Kind.MEDIUM, Kind.WIDE -> Layout.MEDIUM
            Kind.LARGE -> Layout.FULL
        }

    companion object {
        const val ACTION_REFRESH = "com.shinyalatte.btwidget.REFRESH"
        private val ALL = listOf(BtWidgetProvider(), BtWidgetSmall(), BtWidgetMedium(), BtWidgetWide())

        fun draw(c: Context, s: Ui.Snap) {
            val mgr = AppWidgetManager.getInstance(c)
            ALL.forEach { p ->
                val ids = mgr.getAppWidgetIds(ComponentName(c, p.javaClass))
                for (id in ids) {
                    mgr.updateAppWidget(id, p.buildForWidget(c, s, mgr.getAppWidgetOptions(id)))
                }
            }
        }

        internal fun layoutFor(width: Float, height: Float): Layout = when {
            width >= 220 && height >= 40 -> Layout.FULL
            width >= 105 && height >= 36 -> Layout.MEDIUM
            else -> Layout.COMPACT
        }
    }

    @Suppress("DEPRECATION")
    internal fun buildForWidget(c: Context, s: Ui.Snap, options: Bundle): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val layouts = sizes.orEmpty().filter { it.width > 0 && it.height > 0 }
                .take(16).associateWith { buildForSize(c, s, it.width, it.height) }
            if (layouts.isNotEmpty()) return RemoteViews(layouts)
        }

        val portraitWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val portraitHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val landscapeWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val landscapeHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val portrait = if (portraitWidth > 0) {
            buildForSize(c, s, portraitWidth.toFloat(), if (portraitHeight > 0) portraitHeight.toFloat() else 40f)
        } else null
        val landscape = if (landscapeWidth > 0) {
            buildForSize(c, s, landscapeWidth.toFloat(), if (landscapeHeight > 0) landscapeHeight.toFloat() else 40f)
        } else null
        return when {
            portrait != null && landscape != null -> RemoteViews(landscape, portrait)
            else -> portrait ?: landscape ?: build(c, s)
        }
    }

    internal fun buildForSize(c: Context, s: Ui.Snap, width: Float, height: Float): RemoteViews =
        render(c, s, layoutFor(width, height), showCompactDevice = height >= 66, showIcon = width >= 125)

    fun build(c: Context, s: Ui.Snap): RemoteViews = render(c, s, defaultLayout)

    private fun render(
        c: Context,
        s: Ui.Snap,
        layout: Layout,
        showCompactDevice: Boolean = false,
        showIcon: Boolean = true
    ): RemoteViews {
        val v = RemoteViews(c.packageName, layout.resource)
        val light = Appearance.light(c)
        v.setInt(R.id.root, "setBackgroundResource", if (Appearance.mode(c) == Appearance.SYSTEM) R.drawable.widget_bg_system else if (light) R.drawable.widget_bg_light else R.drawable.widget_bg)
        v.setTextColor(R.id.device, Appearance.text(c))
        val color = when (s.state) {
            State.CONNECTED -> Color.parseColor(if (light) "#08764F" else "#59E3AB")
            State.IDLE, State.OFF -> Color.parseColor(if (light) "#B62B3B" else "#FF8D98")
            else -> Appearance.muted(c)
        }
        // Resource actions are resolved by the launcher on configuration changes (Android 12+).
        val systemResources = Build.VERSION.SDK_INT >= 31 && Appearance.mode(c) == Appearance.SYSTEM
        val statusRes = when (s.state) {
            State.CONNECTED -> R.color.widget_connected
            State.IDLE, State.OFF -> R.color.widget_off
            else -> R.color.widget_muted
        }
        if (systemResources) v.setColorStateList(R.id.device, "setTextColor", R.color.widget_text)
        val compact = layout == Layout.COMPACT
        if (!compact) {
            v.setViewVisibility(R.id.icon, if (showIcon) View.VISIBLE else View.GONE)
            when {
                systemResources -> v.setColorStateList(R.id.icon, "setImageTintList", statusRes)
                Build.VERSION.SDK_INT >= 31 -> v.setColorStateList(R.id.icon, "setImageTintList", android.content.res.ColorStateList.valueOf(color))
                else -> v.setInt(R.id.icon, "setColorFilter", color)
            }
        }
        val noPerm = s.state == State.NOPERM
        val connected = s.state == State.CONNECTED
        val showBtn = connected || s.state == State.IDLE
        val full = layout == Layout.FULL
        val status = if (!full) when (s.state) {
            State.CONNECTED -> "接続中"
            State.IDLE -> "未接続"
            State.OFF -> "BTオフ"
            State.NOPERM -> "要許可"
            State.NONE -> "非対応"
        } else Ui.statusText(s)

        v.setTextViewText(R.id.status, status)
        v.setTextColor(R.id.status, color)
        if (systemResources) v.setColorStateList(R.id.status, "setTextColor", statusRes)
        val device = when {
            compact && s.state == State.IDLE -> s.lastName.orEmpty()
            noPerm -> "タップして許可"
            else -> Ui.deviceText(s)
        }
        v.setTextViewText(R.id.device, device)
        if (compact) {
            val visible = showCompactDevice && showBtn && device.isNotEmpty()
            v.setViewVisibility(R.id.device, if (visible) View.VISIBLE else View.GONE)
        }
        v.setOnClickPendingIntent(R.id.root, when {
            noPerm -> Ui.openApp(c, 2)
            s.state == State.OFF -> Ui.settings(c, 3)
            compact -> Ui.action(c, "toggle", 4)
            else -> Ui.picker(c, 5)
        })

        v.setViewVisibility(R.id.btn_main, if (showBtn) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.btn_main, if (connected) "切断" else "再接続")
        v.setTextColor(R.id.btn_main, Color.WHITE)
        v.setInt(R.id.btn_main, "setBackgroundResource", if (connected) R.drawable.btn_red_rect else R.drawable.btn_green_rect)
        v.setOnClickPendingIntent(R.id.btn_main, Ui.action(c, if (connected) "disconnect" else "reconnect", if (connected) 6 else 7))
        if (full) {
            v.setInt(R.id.btn_switch, "setBackgroundResource", if (Appearance.mode(c) == Appearance.SYSTEM) R.drawable.btn_gray_system else if (light) R.drawable.btn_gray_light else R.drawable.btn_gray_rect)
            v.setTextColor(R.id.btn_switch, Appearance.text(c))
            if (systemResources) v.setColorStateList(R.id.btn_switch, "setTextColor", R.color.widget_text)
            v.setViewVisibility(R.id.btn_switch, if (s.state == State.OFF) View.GONE else View.VISIBLE)
            v.setOnClickPendingIntent(R.id.btn_switch, if (s.state == State.OFF) Ui.settings(c, 3) else Ui.picker(c, 8))
        }
        return v
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pr = goAsync(); Ui.refreshAll(c) { pr.finish() }
    }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        Ui.snapshot(c) { snap ->
            try {
                m.updateAppWidget(id, buildForWidget(c, snap, m.getAppWidgetOptions(id)))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(c: Context, i: Intent) {
        val a = i.action
        when {
            a == null || a.startsWith("android.appwidget.") -> super.onReceive(c, i)
            a == ACTION_REFRESH || a == Intent.ACTION_CONFIGURATION_CHANGED -> { val pr = goAsync(); Ui.refreshAll(c) { pr.finish() } }
            else -> {
                val pr = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({ Ui.refreshAll(c) { pr.finish() } }, 1500)
            }
        }
    }
}

/** 1×1 */
class BtWidgetSmall : BtWidgetProvider() {
    override val kind = Kind.SMALL
}

/** 2×1 */
class BtWidgetMedium : BtWidgetProvider() {
    override val kind = Kind.MEDIUM
}

/** 3×1 */
class BtWidgetWide : BtWidgetProvider() {
    override val kind = Kind.WIDE
}
