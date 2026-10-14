package com.shinyalatte.btwidget

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(TextView(this).apply {
            setPadding(48, 96, 48, 48); textSize = 16f
            text = "ラテBT\n\n1. Bluetoothの権限を許可\n2. ホーム画面を長押し →ウィジェット →「ラテBT」から好きなサイズ（1×1／2×1／4×1）を配置\n\nタップ: 表示を更新\n切断ボタン: 接続中の機器を切断（失敗時は設定画面を開きます）"
        })
        if (Build.VERSION.SDK_INT >= 31 && !Bt.hasPerm(this))
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        BtWidgetProvider.requestRefresh(this)
    }
}
