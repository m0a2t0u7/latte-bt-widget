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
        super.onCreate(s)
        if (!Bt.hasPerm(this)) { startActivity(Intent(this, MainActivity::class.java)); finish(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(12)) }
        box.addView(TextView(this).apply {
            text = "ラテBT  機器を選ぶ"; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F2F2F2")); setPadding(dp(4), 0, 0, dp(12))
        })
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { height = LinearLayout.LayoutParams.WRAP_CONTENT })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        bottom.addView(btn("Bluetooth設定", "#3A3F47") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); finish()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        bottom.addView(btn("閉じる", "#3A3F47") { finish() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        box.addView(bottom)
        box.background = GradientDrawable().apply { setColor(Color.parseColor("#202326")); cornerRadius = dp(20).toFloat() }
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
                    if (connected) "#1F7A63" else "#2C3036") {
                    startActivity(Intent(this, ActionActivity::class.java)
                        .putExtra("mode", if (connected) "disconnect" else "connect").putExtra("addr", d.address))
                    finish()
                }
                row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                row.setPadding(dp(14), 0, dp(14), 0)
                listBox.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun note(t: String) = TextView(this).apply { text = t; setTextColor(Color.parseColor("#B0B5BD")); setPadding(dp(4), dp(8), 0, dp(8)) }
    private fun btn(t: String, bg: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 1
        background = GradientDrawable().apply { setColor(Color.parseColor(bg)); cornerRadius = dp(12).toFloat() }
        setOnClickListener { onClick() }
    }
}
