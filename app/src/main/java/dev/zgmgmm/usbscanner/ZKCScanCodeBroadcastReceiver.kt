package dev.zgmgmm.esls.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

/**
 * 监听zkc手持终端扫码广播
 */
class ZKCScanCodeBroadcastReceiver(var onScanCode: (String) -> Unit) : BroadcastReceiver() ,AnkoLogger{
    companion object {
        fun register(context: Context, onScanCode: (String) -> Unit): ZKCScanCodeBroadcastReceiver {
            val scanBroadcastReceiver = ZKCScanCodeBroadcastReceiver(onScanCode)
            val intentFilter = IntentFilter()
            intentFilter.addAction("com.zkc.scancode")
            context.registerReceiver(scanBroadcastReceiver, intentFilter)
            return scanBroadcastReceiver
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.extras?.getString("code")
        info("scan code :$text")
        if (text != null)
            onScanCode(text)
    }
}