package com.shinyalatte.btwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

/** 画面を出さずに 切断／再接続／切替／トグル を実行する */
class ActionActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        app = applicationContext
        if (!Bt.hasPerm(this)) { startActivity(Intent(this, MainActivity::class.java)); finish(); return }
        val a = Bt.adapter(this)
        if (a == null || !a.isEnabled) { toast("Bluetoothがオフです"); openSettings(); return }
        when (intent.getStringExtra("mode")) {
            "disconnect" -> disconnect()
            "reconnect" -> reconnect()
            "connect" -> intent.getStringExtra("addr")?.let { connectTo(it) } ?: done()
            "toggle" -> Bt.withProxies(this) { list ->
                if (Bt.connected(list).isNotEmpty()) disconnect() else reconnect()
            }
            else -> done()
        }
    }

    private fun disconnect() = Bt.withProxies(this) { list ->
        val devs = Bt.connected(list)
        if (devs.isEmpty()) { toast("接続中の機器はありません"); done(); return@withProxies }
        Bt.saveLast(app, devs.first())
        val failed = devs.filterNot { Bt.tryDisconnect(list, it) }
        if (failed.isEmpty()) { toast("切断: " + devs.joinToString { Bt.name(it) }); done() }
        else { toast("この端末では直接切断できません。設定から切断してください"); openSettings() }
    }

    private fun reconnect() {
        val addr = Bt.lastAddr(app)
        if (addr == null) { startActivity(Intent(this, PickerActivity::class.java)); done(); return }
        connectTo(addr)
    }

    /** 他の機器を切ってから target につなぐ */
    private fun connectTo(addr: String) {
        val target = Bt.device(app, addr) ?: run { toast("機器が見つかりません"); done(); return }
        Bt.withProxies(app) { list ->
            val others = Bt.connected(list).filter { it.address != addr }
            others.forEach { Bt.tryDisconnect(list, it) }
            h.postDelayed({
                Bt.withProxies(app) { list2 ->
                    if (Bt.tryConnect(list2, target)) {
                        Bt.saveLast(app, target)
                        toast("接続中: " + Bt.name(target)); done()
                    } else {
                        toast("この端末では直接接続できません。設定から接続してください"); openSettings()
                    }
                }
            }, if (others.isEmpty()) 0L else 1200L)
        }
    }

    private fun toast(m: String) = Toast.makeText(app, m, Toast.LENGTH_SHORT).show()
    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); done()
    }
    private fun done() {
        h.postDelayed({ Ui.refreshAll(app) }, 1800)
        h.postDelayed({ Ui.refreshAll(app) }, 5000)
        finish()
    }
}
