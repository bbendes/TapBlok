package com.cj.tapblok

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.app.Service
import android.os.Build
import android.os.Parcelable

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Whether a normal monitoring session is active. NOTE: this intentionally tracks the monitoring
 * session, NOT mere service liveness — the service can be alive purely for Timeout mode or an
 * Emergency block while monitoring is off, and UI/tag logic must not treat those as a session.
 */
fun isServiceRunning(@Suppress("UNUSED_PARAMETER") context: Context, serviceClass: Class<out Service>): Boolean {
    return serviceClass == AppMonitoringService::class.java && AppMonitoringService.isMonitoringActive
}

fun startMonitoringService(context: Context) =
    sendServiceAction(context, AppMonitoringService.ACTION_START_MONITORING)

/** Restore enforcement (monitoring/timeout/emergency) from persisted state — used after boot. */
fun restoreMonitoringService(context: Context) =
    sendServiceAction(context, AppMonitoringService.ACTION_RESTORE)

/**
 * @param ensureForeground true to start the service (startForegroundService — the handler must
 *   then call startForeground). false for actions whose handler may stopSelf without promoting to
 *   foreground; only safe when the caller is itself a foreground component (all our callers are
 *   foreground Activities).
 */
private fun sendServiceAction(
    context: Context,
    action: String,
    ensureForeground: Boolean = true,
    configure: Intent.() -> Unit = {},
) {
    val intent = Intent(context, AppMonitoringService::class.java).apply {
        this.action = action
        configure()
    }
    if (ensureForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

/** Stop the normal monitoring session without killing the service if Timeout/Emergency still need it. */
fun stopMonitoring(context: Context) =
    sendServiceAction(context, AppMonitoringService.ACTION_STOP_MONITORING, ensureForeground = false)

/** Always (re)start a full-duration Timeout. Tapping again restarts the timer. */
fun startTimeout(context: Context) =
    sendServiceAction(context, AppMonitoringService.ACTION_START_TIMEOUT)

/** End an active Timeout early (used by the Toggle tag while Timeout is active). */
fun endTimeout(context: Context) =
    sendServiceAction(context, AppMonitoringService.ACTION_END_TIMEOUT, ensureForeground = false)

/** Emergency-block [packageName] for 24h, independent of the monitoring session. */
fun addEmergencyBlock(context: Context, packageName: String) =
    sendServiceAction(context, AppMonitoringService.ACTION_ADD_EMERGENCY_BLOCK) {
        putExtra(AppMonitoringService.EXTRA_EMERGENCY_PACKAGE, packageName)
    }

/**
 * Packages to ignore when resolving "the app the user was in" for an Emergency tag. Scanning an
 * NFC tag momentarily brings the NFC dispatcher (and sometimes system UI) to the foreground, so
 * those events would otherwise mask the real app underneath.
 */
private val TRANSIENT_FOREGROUND_PACKAGES = setOf(
    "com.google.android.nfc",
    "com.android.nfc",
    "com.android.systemui",
)

/**
 * The most recent foreground app package (excluding TapBlok and transient NFC/system packages)
 * over the last [lookbackMs]. Used by the Emergency tag to learn which app the user was in at the
 * moment of the tap. Returns null if none can be determined.
 */
fun recentForegroundPackage(context: Context, lookbackMs: Long = 10_000L): String? {
    if (!hasUsageStatsPermission(context)) return null
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val events = usm.queryEvents(now - lookbackMs, now)
    val event = UsageEvents.Event()
    var latest: String? = null
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        ) {
            val pkg = event.packageName
            if (pkg != context.packageName && pkg !in TRANSIENT_FOREGROUND_PACKAGES) {
                latest = pkg
            }
        }
    }
    return latest
}

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key) as? T
    }

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Intent.getParcelableArrayExtraCompat(key: String): Array<out Parcelable>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayExtra(key, T::class.java)
    } else {
        getParcelableArrayExtra(key)
    }
