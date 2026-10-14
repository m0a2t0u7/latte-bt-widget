package com.shinyalatte.btwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** 状態の取得と、ウィジェット・常時通知の一括更新 */
object Ui {
    enum class State { CONNECTED, IDLE, OFF, NOPERM, NONE }
    data class Snap(val state: State, val names: List<String>, val lastName: String?)

    fun snapshot(c: Context, done: (Snap) -> Unit) {
        val a = Bt.adapter(c)
        val last = Bt.lastName(c)
        when {
            a == null -> done(Snap(State.NONE, emptyList(), last))
            !Bt.hasPerm(c) -> done(Snap(State.NOPERM, emptyList(), last))
            !a.isEnabled -> done(Snap(State.OFF, emptyList(), last))
            else -> Bt.withProxies(c) { list ->
                val devs = Bt.connected(list)
                devs.firstOrNull()?.let { Bt.saveLast(c, it) }
                val names = devs.map { Bt.name(it) }
                done(Snap(if (names.isEmpty()) State.IDLE else State.CONNECTED, names, Bt.lastName(c)))
            }
        }
    }

    fun refreshAll(c: Context, onDone: () -> Unit = {}) = snapshot(c) { s ->
        BtWidgetProvider.draw(c, s)
        if (Bt.notifEnabled(c)) Notif.show(c, s)
        onDone()
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun action(c: Context, mode: String, rc: Int, addr: String? = null): PendingIntent =
        PendingIntent.getActivity(c, rc, Intent(c, ActionActivity::class.java)
            .putExtra("mode", mode).putExtra("addr", addr)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), FLAGS)

    fun picker(c: Context, rc: Int): PendingIntent =
        PendingIntent.getActivity(c, rc, Intent(c, PickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)

    fun openApp(c: Context, rc: Int): PendingIntent =
        PendingIntent.getActivity(c, rc, Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)

    fun settings(c: Context, rc: Int): PendingIntent =
        PendingIntent.getActivity(c, rc, Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)

    fun deviceText(s: Snap): String = when (s.state) {
        State.CONNECTED -> s.names.joinToString(" / ")
        State.IDLE -> s.lastName?.let { "再接続：$it" } ?: "未接続"
        State.OFF -> "BTオフ"
        State.NOPERM -> "要許可"
        State.NONE -> "非対応"
    }

    fun statusText(s: Snap): String = when (s.state) {
        State.CONNECTED -> if (s.names.size > 1) "接続中（${s.names.size}台）" else "接続中"
        State.IDLE -> "BT オン・未接続"
        State.OFF -> "BT オフ"
        State.NOPERM -> "権限が必要です"
        State.NONE -> "Bluetooth非対応"
    }
}
