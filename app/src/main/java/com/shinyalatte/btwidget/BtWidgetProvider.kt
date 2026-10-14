package com.shinyalatte.btwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import com.shinyalatte.btwidget.Ui.State

/** 4×1（横長） */
open class BtWidgetProvider : AppWidgetProvider() {
    open val layout = R.layout.widget_bt
    open val kind = Kind.LARGE
    enum class Kind { SMALL, MEDIUM, LARGE }

    companion object {
        const val ACTION_REFRESH = "com.shinyalatte.btwidget.REFRESH"
        private val ALL = listOf(BtWidgetProvider(), BtWidgetSmall(), BtWidgetMedium())

        fun draw(c: Context, s: Ui.Snap) {
            val mgr = AppWidgetManager.getInstance(c)
            ALL.forEach { p ->
                val ids = mgr.getAppWidgetIds(ComponentName(c, p.javaClass))
                if (ids.isNotEmpty()) mgr.updateAppWidget(ids, p.build(c, s))
            }
        }
    }

    fun build(c: Context, s: Ui.Snap): RemoteViews {
        val v = RemoteViews(c.packageName, layout)
        val color = when (s.state) {
            State.CONNECTED -> Color.parseColor("#3DDC97")
            State.OFF -> Color.parseColor("#E0565B")
            else -> Color.parseColor("#8A8F98")
        }
        v.setInt(R.id.icon, "setColorFilter", color)
        val noPerm = s.state == State.NOPERM

        if (kind == Kind.SMALL) {
            val t = when (s.state) {
                State.CONNECTED -> s.names.first().take(6)
                State.IDLE -> s.lastName?.let { "↻" + it.take(5) } ?: "未接続"
                else -> Ui.deviceText(s)
            }
            v.setTextViewText(R.id.device, t)
            v.setTextColor(R.id.device, if (s.state == State.CONNECTED) Color.WHITE else color)
            v.setOnClickPendingIntent(R.id.root, when {
                noPerm -> Ui.openApp(c, 2)
                s.state == State.OFF -> Ui.settings(c, 3)
                else -> Ui.action(c, "toggle", 4)
            })
            return v
        }

        v.setTextViewText(R.id.status, Ui.statusText(s))
        v.setTextColor(R.id.status, color)
        v.setTextViewText(R.id.device, if (noPerm) "タップして許可" else Ui.deviceText(s))
        v.setOnClickPendingIntent(R.id.root, if (noPerm) Ui.openApp(c, 2) else Ui.picker(c, 5))

        val connected = s.state == State.CONNECTED
        val showBtn = connected || s.state == State.IDLE
        v.setViewVisibility(R.id.btn_main, if (showBtn) View.VISIBLE else View.GONE)
        val large = kind == Kind.LARGE
        v.setTextViewText(R.id.btn_main, when {
            large -> if (connected) "切断" else "再接続"
            else -> if (connected) "×" else "↻"
        })
        v.setInt(R.id.btn_main, "setBackgroundResource", when {
            large -> if (connected) R.drawable.btn_red_rect else R.drawable.btn_green_rect
            else -> if (connected) R.drawable.btn_red else R.drawable.btn_green
        })
        v.setOnClickPendingIntent(R.id.btn_main, Ui.action(c, if (connected) "disconnect" else "reconnect", if (connected) 6 else 7))
        if (large) {
            v.setViewVisibility(R.id.btn_switch, if (s.state == State.OFF) View.GONE else View.VISIBLE)
            v.setOnClickPendingIntent(R.id.btn_switch, if (s.state == State.OFF) Ui.settings(c, 3) else Ui.picker(c, 8))
        }
        return v
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pr = goAsync(); Ui.refreshAll(c) { pr.finish() }
    }

    override fun onReceive(c: Context, i: Intent) {
        val a = i.action
        when {
            a == null || a.startsWith("android.appwidget.") -> super.onReceive(c, i)
            a == ACTION_REFRESH -> { val pr = goAsync(); Ui.refreshAll(c) { pr.finish() } }
            else -> {
                val pr = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({ Ui.refreshAll(c) { pr.finish() } }, 1500)
            }
        }
    }
}

/** 1×1 */
class BtWidgetSmall : BtWidgetProvider() {
    override val layout = R.layout.widget_small
    override val kind = Kind.SMALL
}

/** 2×1 */
class BtWidgetMedium : BtWidgetProvider() {
    override val layout = R.layout.widget_medium
    override val kind = Kind.MEDIUM
}
