package com.cj.tapblok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.settings.SessionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val monitoringActive = prefs.getBoolean("monitoring_active", false)
        val timeoutActive = SessionSettings.timeoutActive(context, now)

        // Emergency blocks live in the DB; querying them requires async work.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasEmergency = AppDatabase.getDatabase(context)
                    .emergencyBlockDao()
                    .getAllList()
                    .any { it.expiresAtMs > now }

                if (monitoringActive || timeoutActive || hasEmergency) {
                    Log.d(
                        "BootCompletedReceiver",
                        "Boot completed, restoring enforcement (monitoring=$monitoringActive, timeout=$timeoutActive, emergency=$hasEmergency)."
                    )
                    restoreMonitoringService(context)
                } else {
                    Log.d("BootCompletedReceiver", "Boot completed, nothing to enforce — skipping auto-start.")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
