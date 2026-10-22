package com.shinyalatte.btwidget

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** UI preference is independent of saved Bluetooth devices and notification settings. */
object Appearance {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    private fun prefs(c: Context) = c.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun mode(c: Context) = prefs(c).getString("theme", SYSTEM) ?: SYSTEM
    fun save(c: Context, value: String) { prefs(c).edit().putString("theme", value).apply() }
    fun light(c: Context) = when (mode(c)) {
        LIGHT -> true
        DARK -> false
        else -> c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
    }
    fun apply(a: Activity, dialog: Boolean = false) {
        a.setTheme(if (dialog) {
            if (light(a)) android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar else android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
        } else {
            if (light(a)) android.R.style.Theme_DeviceDefault_Light_NoActionBar else android.R.style.Theme_DeviceDefault_NoActionBar
        })
    }
    fun text(c: Context) = Color.parseColor(if (light(c)) "#102A43" else "#F2F5FA")
    fun muted(c: Context) = Color.parseColor(if (light(c)) "#50657B" else "#B8C5D5")
    fun background(c: Context) = Color.parseColor(if (light(c)) "#F5F8FD" else "#151C26")
    fun surface(c: Context) = Color.parseColor(if (light(c)) "#FFFFFF" else "#202B39")
    fun secondary(c: Context) = Color.parseColor(if (light(c)) "#E5EDFA" else "#35445A")
}
