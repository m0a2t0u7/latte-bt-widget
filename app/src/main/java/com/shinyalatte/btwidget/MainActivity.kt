package com.shinyalatte.btwidget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.content.res.ColorStateList
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private lateinit var sw: Switch

    override fun onCreate(s: Bundle?) {
        Appearance.apply(this)
        super.onCreate(s)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
        box.addView(TextView(this).apply { text = "Bluetoothウィジェット｜ラテ"; textSize = 24f; setTextColor(Appearance.text(this@MainActivity)) })
        sw = Switch(this).apply {
            text = "常時通知で表示・操作する"; textSize = 16f; isChecked = Bt.notifEnabled(this@MainActivity)
            setPadding(0, dp(24), 0, dp(16))
            setOnCheckedChangeListener { _, on ->
                Bt.setNotifEnabled(this@MainActivity, on)
                if (on) { ensurePerms(); BtStatusService.start(this@MainActivity) } else BtStatusService.stop(this@MainActivity)
            }
        }
        sw.setTextColor(Appearance.text(this))
        sw.buttonTintList = ColorStateList.valueOf(Color.parseColor("#1262E8"))
        box.addView(sw)
        box.addView(TextView(this).apply {
            text = "外観"; textSize = 18f; setTextColor(Appearance.text(this@MainActivity))
            setPadding(0, dp(8), 0, dp(8))
        })
        val themes = listOf(Appearance.LIGHT to "ライト", Appearance.DARK to "ダーク", Appearance.SYSTEM to "端末設定に合わせる")
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        themes.forEachIndexed { i, (value, label) ->
            group.addView(RadioButton(this).apply {
                id = 100 + i; text = label; textSize = 16f
                setTextColor(Appearance.text(this@MainActivity))
                buttonTintList = ColorStateList.valueOf(Color.parseColor(if (Appearance.light(this@MainActivity)) "#1262E8" else "#8EB9FF"))
                minHeight = dp(48)
            })
        }
        group.check(100 + themes.indexOfFirst { it.first == Appearance.mode(this) }.coerceAtLeast(0))
        group.setOnCheckedChangeListener { _, id ->
            val value = themes[id - 100].first
            if (value != Appearance.mode(this)) {
                Appearance.save(this, value)
                Ui.refreshAll(applicationContext)
                recreate()
            }
        }
        box.addView(group)
        box.addView(TextView(this).apply {
            text = "アプリ画面と、配置済みの全ウィジェットに反映します"; textSize = 13f
            setTextColor(Appearance.muted(this@MainActivity)); setPadding(0, 0, 0, dp(20))
        })
        box.addView(TextView(this).apply {
            text = "機器を選ぶ・切り替える"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#1262E8")); cornerRadius = dp(12).toFloat() }
            setOnClickListener { startActivity(Intent(this@MainActivity, PickerActivity::class.java)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        box.addView(TextView(this).apply {
            setTextColor(Appearance.muted(this@MainActivity)); textSize = 14f; setPadding(0, dp(24), 0, 0)
            text = "ウィジェット：ホーム画面を長押し →ウィジェット →「Bluetoothウィジェット｜ラテ」から1×1／2×1／4×1を配置\n\n" +
                "1×1：タップで切断／直前の機器に再接続\n2×1・4×1：本体タップで機器選択、ボタンで切断・再接続\n" +
                "常時通知：切断／再接続・切替・BT設定ボタン\n\n" +
                "Bluetooth自体のオン/オフはAndroidの制限でアプリから切れないため、BT設定を開きます"
        })
        box.setBackgroundColor(Appearance.background(this))
        setContentView(ScrollView(this).apply { setBackgroundColor(Appearance.background(this@MainActivity)); addView(box) })
        ensurePerms()
    }

    private fun ensurePerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 && !Bt.hasPerm(this)) need += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33 && Bt.notifEnabled(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (Bt.notifEnabled(this)) BtStatusService.start(this)
        Ui.refreshAll(applicationContext)
    }
}
