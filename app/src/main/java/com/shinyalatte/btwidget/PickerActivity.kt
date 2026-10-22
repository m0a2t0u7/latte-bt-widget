package com.shinyalatte.btwidget

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ペアリング済みのオーディオ機器を一覧し、タップで切替・切断 */
class PickerActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        Appearance.apply(this, dialog = true)
        super.onCreate(s)
        if (!Bt.hasPerm(this)) { startActivity(Intent(this, MainActivity::class.java)); finish(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(12)) }
        box.addView(TextView(this).apply {
            text = "Bluetooth機器を選ぶ"; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(Appearance.text(this@PickerActivity)); setPadding(dp(4), 0, 0, dp(12))
        })
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { height = LinearLayout.LayoutParams.WRAP_CONTENT })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        bottom.addView(btn("Bluetooth設定", if (Appearance.light(this)) "#E5EDFA" else "#35445A") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); finish()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        bottom.addView(btn("閉じる", if (Appearance.light(this)) "#E5EDFA" else "#35445A") { finish() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(bottom)
        box.background = GradientDrawable().apply { setColor(Appearance.surface(this)); cornerRadius = dp(20).toFloat() }
        setContentView(box)
        window.setLayout(resources.displayMetrics.widthPixels - dp(32), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)

        val a = Bt.adapter(this)
        if (a == null || !a.isEnabled) { listBox.addView(note("Bluetoothがオフです。下のボタンから設定を開いてください")); return }
        val devs = Bt.audioBonded(this)
        if (devs.isEmpty()) { listBox.addView(note("ペアリング済みのオーディオ機器がありません")); return }
        Bt.withProxies(this) { list ->
            val on = Bt.connected(list).map { it.address }.toSet()
            devs.forEach { d ->
                val connected = d.address in on
                val row = btn((if (connected) "● " else "○ ") + Bt.name(d) + if (connected) "　接続中（タップで切断）" else "",
                    if (connected) if (Appearance.light(this)) "#D9F2E9" else "#1F604E" else if (Appearance.light(this)) "#EDF3FC" else "#2C3849") {
                    startActivity(Intent(this, ActionActivity::class.java)
                        .putExtra("mode", if (connected) "disconnect" else "connect").putExtra("addr", d.address))
                    finish()
                }
                row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                row.setPadding(dp(14), dp(12), dp(14), dp(12))
                row.minHeight = dp(56)
                listBox.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun note(t: String) = TextView(this).apply { text = t; setTextColor(Appearance.muted(this@PickerActivity)); setPadding(dp(4), dp(8), 0, dp(8)) }
    private fun btn(t: String, bg: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Appearance.text(this@PickerActivity)); gravity = Gravity.CENTER; maxLines = 3
        background = GradientDrawable().apply { setColor(Color.parseColor(bg)); cornerRadius = dp(12).toFloat() }
        setOnClickListener { onClick() }
    }
}
