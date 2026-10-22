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
    fun name(d: BluetoothDevice): String = runCatching { (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name }.getOrNull() ?: d.address

    /** ペアリング済みのオーディオ機器（ヘッドホン・イヤホン・スピーカー） */
    @SuppressLint("MissingPermission")
    fun audioBonded(c: Context): List<BluetoothDevice> = runCatching {
        adapter(c)?.bondedDevices.orEmpty().filter {
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        }.sortedBy { name(it) }
    }.getOrDefault(emptyList())

    fun device(c: Context, addr: String): BluetoothDevice? =
        runCatching { adapter(c)?.getRemoteDevice(addr) }.getOrNull()

    /** 非公開API disconnect/connect をリフレクションで呼ぶ。1つでも成功したら true */
    private fun call(list: List<Pair<Int, BluetoothProfile>>, d: BluetoothDevice, method: String): Boolean {
        var ok = false
        list.forEach { (_, proxy) ->
            runCatching {
                val m = proxy.javaClass.getMethod(method, BluetoothDevice::class.java)
                if (m.invoke(proxy, d) == true) ok = true
            }
        }
        return ok
    }
    fun tryDisconnect(list: List<Pair<Int, BluetoothProfile>>, d: BluetoothDevice) = call(list, d, "disconnect")
    fun tryConnect(list: List<Pair<Int, BluetoothProfile>>, d: BluetoothDevice) = call(list, d, "connect")

    // 最後に使った機器（再接続用）
    private fun prefs(c: Context) = c.getSharedPreferences("bt", Context.MODE_PRIVATE)
    fun saveLast(c: Context, d: BluetoothDevice) =
        prefs(c).edit().putString("lastAddr", d.address).putString("lastName", name(d)).apply()
    fun lastAddr(c: Context): String? = prefs(c).getString("lastAddr", null)
    fun lastName(c: Context): String? = prefs(c).getString("lastName", null)

    fun notifEnabled(c: Context) = prefs(c).getBoolean("notif", false)
    fun setNotifEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("notif", v).apply()
}
