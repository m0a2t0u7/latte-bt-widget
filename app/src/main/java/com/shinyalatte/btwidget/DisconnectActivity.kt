package com.shinyalatte.btwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

class DisconnectActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (!Bt.hasPerm(this)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        Bt.withProxies(this) { list ->
            val devs = Bt.connected(list)
            if (devs.isEmpty()) {
                Toast.makeText(this, "接続中の機器はありません", Toast.LENGTH_SHORT).show()
            } else {
                val failed = devs.filterNot { Bt.tryDisconnect(list, it) }
                if (failed.isEmpty()) {
                    Toast.makeText(this, "切断しました: " + devs.joinToString { Bt.name(it) }, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "この端末では直接切断できません。設定から切断してください", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            val app = applicationContext
            Handler(Looper.getMainLooper()).postDelayed({ BtWidgetProvider.requestRefresh(app) }, 1500)
            finish()
        }
    }
}
