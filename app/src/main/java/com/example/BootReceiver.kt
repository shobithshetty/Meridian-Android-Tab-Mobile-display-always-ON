package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reopens Meridian after a restart when "Start on boot" is on. Android 10+ only lets a background app open an
 * activity if it may "display over other apps", so we check that permission first and quietly do nothing without it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = AppDatabase.getDatabase(app).clockDao().getSettingsDirect()?.autoStartOnBoot == true
                if (enabled && Settings.canDrawOverlays(app)) {
                    app.startActivity(
                        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
