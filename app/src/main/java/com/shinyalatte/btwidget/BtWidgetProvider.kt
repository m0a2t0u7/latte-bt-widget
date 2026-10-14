package com.shinyalatte.btwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

class BtWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.shinyalatte.btwidget.REFRESH"

        fun requestRefresh(c: Context) =
            c.sendBroadcast(Intent(c, BtWidgetProvider::class.java).setAction(ACTION_REFRESH))

        fun refresh(c: Context, onDone: () -> Unit = {}) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, BtWidgetProvider::class.java))
            if (ids.isEmpty()) { onDone(); return }
            val v = RemoteViews(c.packageName, R.layout.widget_bt)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(c, 0,
                Intent(c, BtWidgetProvider::class.java).setAction(ACTION_REFRESH), flags))
            v.setOnClickPendingIntent(R.id.btn_disconnect, PendingIntent.getActivity(c, 1,
                Intent(c, DisconnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))

            val a = Bt.adapter(c)
            when {
                a == null -> { v.setTextViewText(R.id.status, "Bluetooth非対応"); v.setTextViewText(R.id.device, ""); push(mgr, ids, v); onDone() }
                !Bt.hasPerm(c) -> {
                    v.setTextViewText(R.id.status, "権限が必要です")
                    v.setTextViewText(R.id.device, "タップして許可")
                    v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 2,
                        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
                    push(mgr, ids, v); onDone()
                }
                !a.isEnabled -> { v.setTextViewText(R.id.status, "BT オフ"); v.setTextViewText(R.id.device, "未接続"); push(mgr, ids, v); onDone() }
                else -> Bt.withProxies(c) { list ->
                    val devs = Bt.connected(list)
                    v.setTextViewText(R.id.status, if (devs.size > 1) "BT オン（${devs.size}台）" else "BT オン")
                    v.setTextViewText(R.id.device, if (devs.isEmpty()) "未接続" else devs.joinToString(" / ") { Bt.name(it) })
                    push(mgr, ids, v); onDone()
                }
            }
        }

        private fun push(m: AppWidgetManager, ids: IntArray, v: RemoteViews) = m.updateAppWidget(ids, v)
    }

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pr = goAsync(); refresh(c) { pr.finish() }
    }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> super.onReceive(c, i)
            ACTION_REFRESH -> { val pr = goAsync(); refresh(c) { pr.finish() } }
            null -> super.onReceive(c, i)
            else -> { // 接続状態の変化。プロファイル反映待ちで少し遅らせる
                val pr = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({ refresh(c) { pr.finish() } }, 1500)
            }
        }
    }
}
