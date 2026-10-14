package com.shinyalatte.btwidget

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

object Bt {
    fun adapter(c: Context): BluetoothAdapter? = c.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPerm(c: Context) = Build.VERSION.SDK_INT < 31 ||
        c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** A2DPとHEADSETのプロキシを取得して done に渡す。done の後でプロキシは閉じる */
    fun withProxies(c: Context, done: (List<Pair<Int, BluetoothProfile>>) -> Unit) {
        val a = adapter(c)
        if (a == null || !a.isEnabled) { done(emptyList()); return }
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        val got = mutableListOf<Pair<Int, BluetoothProfile>>()
        var remaining = profiles.size
        var finished = false
        val h = Handler(Looper.getMainLooper())
        fun finish() {
            if (finished) return
            finished = true
            try { done(got) } finally { got.forEach { runCatching { a.closeProfileProxy(it.first, it.second) } } }
        }
        profiles.forEach { p ->
            val ok = a.getProfileProxy(c.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    h.post { got.add(profile to proxy); remaining--; if (remaining == 0) finish() }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, p)
            if (!ok) remaining--
        }
        if (remaining == 0) finish() else h.postDelayed({ finish() }, 2500)
    }

    @SuppressLint("MissingPermission")
    fun connected(list: List<Pair<Int, BluetoothProfile>>): List<BluetoothDevice> =
        list.flatMap { runCatching { it.second.connectedDevices }.getOrDefault(emptyList()) }.distinctBy { it.address }

    @SuppressLint("MissingPermission")
    fun name(d: BluetoothDevice): String = runCatching { d.alias ?: d.name }.getOrNull() ?: d.address

    /** 非公開API disconnect をリフレクションで呼ぶ。1つでも成功したら true */
    fun tryDisconnect(list: List<Pair<Int, BluetoothProfile>>, d: BluetoothDevice): Boolean {
        var ok = false
        list.forEach { (_, proxy) ->
            runCatching {
                val m = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                if (m.invoke(proxy, d) == true) ok = true
            }
        }
        return ok
    }
}
