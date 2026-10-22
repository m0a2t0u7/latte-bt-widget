package com.shinyalatte.btwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** 常時通知 */
object Notif {
    const val ID = 1
    private const val CH = "status"

    fun channel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "接続状態（常時表示）", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            })
    }

    fun build(c: Context, s: Ui.Snap?): Notification {
        channel(c)
        val b = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_bt)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setContentIntent(Ui.picker(c, 20))
        if (s == null) return b.setContentTitle(c.getString(R.string.app_name)).setContentText("読み込み中").build()
        b.setContentTitle(Ui.statusText(s)).setContentText(Ui.deviceText(s))
            .setColor(when (s.state) {
                Ui.State.CONNECTED -> 0xFF3DDC97.toInt()
                Ui.State.OFF -> 0xFFE0565B.toInt()
                else -> 0xFF8A8F98.toInt()
            })
        when (s.state) {
            Ui.State.CONNECTED -> b.addAction(Notification.Action.Builder(null, "切断", Ui.action(c, "disconnect", 21)).build())
            Ui.State.IDLE -> b.addAction(Notification.Action.Builder(null, "再接続", Ui.action(c, "reconnect", 22)).build())
            Ui.State.NOPERM -> b.setContentIntent(Ui.openApp(c, 25))
            else -> {}
        }
        if (s.state == Ui.State.CONNECTED || s.state == Ui.State.IDLE)
            b.addAction(Notification.Action.Builder(null, "切替", Ui.picker(c, 23)).build())
        b.addAction(Notification.Action.Builder(null, "BT設定", Ui.settings(c, 24)).build())
        return b.build()
    }

    fun show(c: Context, s: Ui.Snap) {
        if (!BtStatusService.running) return
        runCatching { c.getSystemService(NotificationManager::class.java).notify(ID, build(c, s)) }
    }
}
