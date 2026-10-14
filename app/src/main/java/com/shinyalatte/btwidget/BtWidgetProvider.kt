package com.shinyalatte.btwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/** 4×1（横長） */
open class BtWidgetProvider : AppWidgetProvider() {
    open val layout = R.layout.widget_bt
    open val kind = Kind.LARGE

    enum class Kind { SMALL, MEDIUM, LARGE }
    enum class State { CONNECTED, IDLE, OFF, NOPERM, NONE }
    data class Snap(val state: State, val names: List<String>)

    companion object {
        const val ACTION_REFRESH = "com.shinyalatte.btwidget.REFRESH"
        private val ALL = listOf(BtWidgetProvider(), BtWidgetSmall(), BtWidgetMedium())

        fun requestRefresh(c: Context) =
            c.sendBroadcast(Intent(c, BtWidgetProvider::class.java).setAction(ACTION_REFRESH))

        /** 全サイズのウィジェットをまとめて更新 */
        fun refresh(c: Context, onDone: () -> Unit = {}) {
            val a = Bt.adapter(c)
            when {
                a == null -> { draw(c, Snap(State.NONE, emptyList())); onDone() }
                !Bt.hasPerm(c) -> { draw(c, Snap(State.NOPERM, emptyList())); onDone() }
                !a.isEnabled -> { draw(c, Snap(State.OFF, emptyList())); onDone() }
                else -> Bt.withProxies(c) { list ->
                    val names = Bt.connected(list).map { Bt.name(it) }
                    draw(c, Snap(if (names.isEmpty()) State.IDLE else State.CONNECTED, names)); onDone()
                }
            }
        }

        private fun draw(c: Context, s: Snap) {
            val mgr = AppWidgetManager.getInstance(c)
            ALL.forEach { p ->
                val ids = mgr.getAppWidgetIds(ComponentName(c, p.javaClass))
                if (ids.isNotEmpty()) mgr.updateAppWidget(ids, p.build(c, s))
            }
        }
    }

    fun build(c: Context, s: Snap): RemoteViews {
        val v = RemoteViews(c.packageName, layout)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val action = PendingIntent.getActivity(c, 1,
            Intent(c, DisconnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val refresh = PendingIntent.getBroadcast(c, 0,
            Intent(c, BtWidgetProvider::class.java).setAction(ACTION_REFRESH), flags)
        val openApp = PendingIntent.getActivity(c, 2,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)

        val color = when (s.state) {
            State.CONNECTED -> Color.parseColor("#3DDC97")
            State.OFF -> Color.parseColor("#E0565B")
            else -> Color.parseColor("#8A8F98")
        }
        v.setInt(R.id.icon, "setColorFilter", color)

        val device = when (s.state) {
            State.CONNECTED -> s.names.joinToString(" / ")
            State.IDLE -> "未接続"
            State.OFF -> "BTオフ"
            State.NOPERM -> "要許可"
            State.NONE -> "非対応"
        }
        if (kind == Kind.SMALL) {
            val short = if (s.state == State.CONNECTED) s.names.first().take(6) else device
            v.setTextViewText(R.id.device, short)
            v.setTextColor(R.id.device, if (s.state == State.CONNECTED) Color.WHITE else color)
            v.setOnClickPendingIntent(R.id.root, if (s.state == State.NOPERM) openApp else action)
            return v
        }
        val status = when (s.state) {
            State.CONNECTED -> if (s.names.size > 1) "接続中（${s.names.size}台）" else "接続中"
            State.IDLE -> "BT オン"
            State.OFF -> "BT オフ"
            State.NOPERM -> "権限が必要です"
            State.NONE -> "Bluetooth非対応"
        }
        v.setTextViewText(R.id.status, status)
        v.setTextColor(R.id.status, color)
        v.setTextViewText(R.id.device, if (s.state == State.NOPERM) "タップして許可" else device)
        v.setOnClickPendingIntent(R.id.root, if (s.state == State.NOPERM) openApp else refresh)
        v.setOnClickPendingIntent(R.id.btn_disconnect, action)
        return v
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pr = goAsync(); refresh(c) { pr.finish() }
    }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, null -> super.onReceive(c, i)
            ACTION_REFRESH -> { val pr = goAsync(); refresh(c) { pr.finish() } }
            else -> {
                if (i.action?.startsWith("android.appwidget.") == true) { super.onReceive(c, i); return }
                val pr = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({ refresh(c) { pr.finish() } }, 1500)
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
