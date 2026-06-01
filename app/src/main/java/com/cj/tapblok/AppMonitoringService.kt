package com.cj.tapblok

import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.settings.SessionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    @Volatile private var packageToGroupId: Map<String, Long?> = emptyMap()
    @Volatile private var isBreakActive = false
    @Volatile private var currentBreakGroupId: Long? = null
    private var isMonitoring = false
    private var breakTimer: CountDownTimer? = null
    private var lastForeground = ForegroundInfo(null, null)
    private var lastEventsQueryTime = 0L

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        const val EXTRA_BLOCKED_APP_PACKAGE_NAME = "BLOCKED_APP_PACKAGE_NAME"
        const val EXTRA_BLOCKED_GROUP_ID = "BLOCKED_GROUP_ID"
        const val EXTRA_BREAK_GROUP_ID = "BREAK_GROUP_ID"
        private const val NO_GROUP_SENTINEL = Long.MIN_VALUE
        @Volatile var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BREAK) {
            val groupIdRaw = intent.getLongExtra(EXTRA_BREAK_GROUP_ID, NO_GROUP_SENTINEL)
            val groupId = if (groupIdRaw == NO_GROUP_SENTINEL) null else groupIdRaw
            startBreak(groupId)
            return START_NOT_STICKY
        }

        Log.d("AppMonitoringService", "Service has started.")

        prefs.edit {
            putInt("blocked_app_attempts", 0)
            putBoolean("monitoring_active", true)
        }
        initSessionCounters()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapBlok is Active")
            .setContentText("App monitoring and blocking is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (isMonitoring) return START_STICKY
        isMonitoring = true

        serviceScope.launch {
            db.blockedAppDao().getAllBlockedApps().collect { list ->
                packageToGroupId = list.associate { it.packageName to it.groupId }
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked apps updated from DB: $packageToGroupId")
            }
        }

        serviceScope.launch {
            val localContext = this@AppMonitoringService

            while (isActive) {
                if (!hasUsageStatsPermission(localContext) || !Settings.canDrawOverlays(localContext)) {
                    Log.e("AppMonitoringService", "Permissions revoked. Stopping service.")
                    stopSelf()
                    break
                }

                if (!isBreakActive) {
                    val foreground = getForegroundInfo()
                    val foregroundApp = foreground.packageName
                    if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Foreground: $foregroundApp / ${foreground.className}")

                    if (foregroundApp != null && packageToGroupId.containsKey(foregroundApp) && foregroundApp != packageName) {
                        val groupId = packageToGroupId[foregroundApp]
                        val blockIntent = Intent(localContext, BlockingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(EXTRA_BLOCKED_APP_PACKAGE_NAME, foregroundApp)
                            if (groupId != null) putExtra(EXTRA_BLOCKED_GROUP_ID, groupId)
                        }
                        startActivity(blockIntent)
                        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked app detected: $foregroundApp (group=$groupId)")

                        val attempts = prefs.getInt("blocked_app_attempts", 0)
                        prefs.edit {
                            putInt("blocked_app_attempts", attempts + 1)
                        }
                    } else if (isDeviceAdminActive() && isUninstallPath(foreground)) {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(home)
                        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Uninstall path detected (${foreground.packageName}/${foreground.className}) — redirecting home.")
                    }
                }
                delay(1000)
            }
        }

        return START_STICKY
    }

    /**
     * Wipe stale per-group session state and initialize fresh counters for every existing group
     * (plus the ungrouped bucket). Each group's break budget is its override or the global default.
     */
    private fun initSessionCounters() {
        SessionSettings.clearAllGroupSessionState(this)
        val globalCount = SessionSettings.breakCount(this)
        SessionSettings.setBreaksRemaining(this, groupId = null, value = globalCount)
        serviceScope.launch {
            val groups = db.appGroupDao().getAllList()
            for (group in groups) {
                SessionSettings.setBreaksRemaining(
                    this@AppMonitoringService,
                    groupId = group.id,
                    value = group.breakCount ?: globalCount
                )
            }
        }
    }

    private fun startBreak(groupId: Long?) {
        breakTimer?.cancel()
        isBreakActive = true
        currentBreakGroupId = groupId
        Log.d("AppMonitoringService", "Break started (group=$groupId).")

        // DAO lookup on IO; CountDownTimer must be constructed on a Looper thread (Main).
        serviceScope.launch {
            val durationMs = effectiveBreakDurationMs(groupId)
            withContext(Dispatchers.Main) {
                breakTimer = object : CountDownTimer(durationMs, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        isBreakActive = false
                        val finishedGroupId = currentBreakGroupId
                        currentBreakGroupId = null
                        SessionSettings.setGroupLastBreakEndedAtMs(
                            this@AppMonitoringService,
                            finishedGroupId,
                            System.currentTimeMillis()
                        )
                        Log.d("AppMonitoringService", "Break finished (group=$finishedGroupId).")
                    }
                }.start()
            }
        }
    }

    private suspend fun effectiveBreakDurationMs(groupId: Long?): Long {
        val global = SessionSettings.breakDurationMs(this)
        if (groupId == null) return global
        val group = db.appGroupDao().getById(groupId) ?: return global
        return group.breakDurationMs ?: global
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        prefs.edit { putBoolean("monitoring_active", false) }
        SessionSettings.clearAllGroupSessionState(this)
        serviceScope.cancel()
        breakTimer?.cancel()
        Log.d("AppMonitoringService", "Service has been destroyed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, TapBlokDeviceAdminReceiver::class.java))
    }

    private data class ForegroundInfo(val packageName: String?, val className: String?)

    /**
     * Walk every new MOVE_TO_FOREGROUND / ACTIVITY_RESUMED event since the last poll and update
     * `lastForeground`. If no events fired in this window (user sitting still on a screen),
     * `lastForeground` keeps its prior value — important because the redirect target screens
     * (app info, device admin) emit no further events while sitting on them.
     */
    private fun getForegroundInfo(): ForegroundInfo {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastEventsQueryTime == 0L) now - 60_000L else lastEventsQueryTime
        val events = usm.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                lastForeground = ForegroundInfo(event.packageName, event.className)
            }
        }
        lastEventsQueryTime = now
        return lastForeground
    }

    /**
     * True when the foreground screen is one the user could use to defeat the session:
     * Android Settings (the uninstall + device-admin revoke paths both live there, and modern
     * Pixel collapses sub-screens into a generic SubSettings host activity so we can't
     * distinguish them by class name) or the package installer's confirm-uninstall flow.
     */
    private fun isUninstallPath(info: ForegroundInfo): Boolean {
        return when (info.packageName) {
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller" -> true
            else -> false
        }
    }
}
