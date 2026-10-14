package com.shinyalatte.btwidget

import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** 常時通知を出し続け、接続の変化で内容を更新する */
class BtStatusService : Service() {
    companion object {
        @Volatile var running = false
        fun start(c: Context) { if (Bt.hasPerm(c)) c.startForegroundService(Intent(c, BtStatusService::class.java)) }
        fun stop(c: Context) = c.stopService(Intent(c, BtStatusService::class.java))
    }

    private val h = Handler(Looper.getMainLooper())
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            h.removeCallbacksAndMessages(null)
            h.postDelayed({ Ui.refreshAll(applicationContext) }, 1200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val n = Notif.build(this, null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(Notif.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(Notif.ID, n)
        running = true
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED) else registerReceiver(rx, f)
        Ui.refreshAll(applicationContext)
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int { Ui.refreshAll(applicationContext); return START_STICKY }
    override fun onDestroy() { running = false; runCatching { unregisterReceiver(rx) }; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}

/** 再起動後に常時通知を復帰 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Bt.notifEnabled(c)) runCatching { BtStatusService.start(c) }
    }
}
