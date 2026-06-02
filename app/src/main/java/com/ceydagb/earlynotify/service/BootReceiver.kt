package com.ceydagb.earlynotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ceydagb.earlynotify.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Cihaz yeniden başladığında, izleme açık bırakılmışsa servisi tekrar başlatır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(appContext).settings.first()
                if (settings.monitoringEnabled) {
                    MonitorService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
