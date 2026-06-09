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
import com.cj.tapblok.database.EmergencyBlock
import com.cj.tapblok.database.GroupTimeRule
import com.cj.tapblok.settings.GlobalBreakSettings
import com.cj.tapblok.settings.ResolvedGroupSettings
import com.cj.tapblok.settings.SessionSettings
import com.cj.tapblok.settings.currentDayOfWeekBit
import com.cj.tapblok.settings.currentMinuteOfDay
import com.cj.tapblok.settings.resolveForGroup
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
    @Volatile private var timeRulesByGroup: Map<Long, List<GroupTimeRule>> = emptyMap()
    @Volatile private var emergencyBlocks: Map<String, Long> = emptyMap()
    @Volatile private var currentBreakGroupId: Long? = null
    private var isMonitoring = false
    private var breakTimer: CountDownTimer? = null
    private var lastForeground = ForegroundInfo(null, null)
    private var lastEventsQueryTime = 0L

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        const val ACTION_END_BREAK = "com.cj.tapblok.ACTION_END_BREAK"
        const val ACTION_START_MONITORING = "com.cj.tapblok.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.cj.tapblok.ACTION_STOP_MONITORING"
        const val ACTION_START_TIMEOUT = "com.cj.tapblok.ACTION_START_TIMEOUT"
        const val ACTION_END_TIMEOUT = "com.cj.tapblok.ACTION_END_TIMEOUT"
        const val ACTION_ADD_EMERGENCY_BLOCK = "com.cj.tapblok.ACTION_ADD_EMERGENCY_BLOCK"
        const val ACTION_RESTORE = "com.cj.tapblok.ACTION_RESTORE"
        const val EXTRA_BLOCKED_APP_PACKAGE_NAME = "BLOCKED_APP_PACKAGE_NAME"
        const val EXTRA_BLOCKED_GROUP_ID = "BLOCKED_GROUP_ID"
        const val EXTRA_BREAK_GROUP_ID = "BREAK_GROUP_ID"
        const val EXTRA_EMERGENCY_PACKAGE = "EMERGENCY_PACKAGE"
        const val EXTRA_BLOCK_MODE = "BLOCK_MODE"
        const val BLOCK_MODE_NORMAL = "normal"
        const val BLOCK_MODE_TIMEOUT = "timeout"
        const val BLOCK_MODE_EMERGENCY = "emergency"
        private const val NO_GROUP_SENTINEL = Long.MIN_VALUE
        private const val EMERGENCY_BLOCK_MS = 24L * 60L * 60_000L
        @Volatile var isRunning = false
        @Volatile var isMonitoringActive = false
        @Volatile var isBreakActive = false
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BREAK -> {
                val groupIdRaw = intent.getLongExtra(EXTRA_BREAK_GROUP_ID, NO_GROUP_SENTINEL)
                val groupId = if (groupIdRaw == NO_GROUP_SENTINEL) null else groupIdRaw
                startBreak(groupId)
                return START_NOT_STICKY
            }

            ACTION_END_BREAK -> {
                if (isBreakActive) {
                    breakTimer?.cancel()
                    finishBreak()
                }
                return START_NOT_STICKY
            }

            ACTION_STOP_MONITORING -> {
                Log.d("AppMonitoringService", "Monitoring stopped (timeout/emergency may keep the service alive).")
                isMonitoringActive = false
                prefs.edit { putBoolean("monitoring_active", false) }
                SessionSettings.clearAllGroupSessionState(this)
                if (!stopIfNoReason()) ensureLoopStarted()
                return START_STICKY
            }

            ACTION_START_TIMEOUT -> {
                val endsAt = System.currentTimeMillis() + SessionSettings.timeoutDurationMs(this)
                SessionSettings.setTimeoutEndsAtMs(this, endsAt)
                Log.d("AppMonitoringService", "Timeout (re)started until $endsAt.")
                ensureLoopStarted()
                return START_STICKY
            }

            ACTION_END_TIMEOUT -> {
                SessionSettings.setTimeoutEndsAtMs(this, 0L)
                Log.d("AppMonitoringService", "Timeout ended.")
                if (!stopIfNoReason()) ensureLoopStarted()
                return START_STICKY
            }

            ACTION_ADD_EMERGENCY_BLOCK -> {
                val pkg = intent.getStringExtra(EXTRA_EMERGENCY_PACKAGE)
                if (pkg != null) {
                    val expiresAt = System.currentTimeMillis() + EMERGENCY_BLOCK_MS
                    // Optimistically register in memory so the poll loop's stopIfNoReason() check
                    // sees an active block immediately — the DB insert + observer are async and
                    // would otherwise let the service self-stop on the first tick when monitoring
                    // is off. The observer reconciles this map shortly after.
                    emergencyBlocks = emergencyBlocks + (pkg to expiresAt)
                    serviceScope.launch {
                        db.emergencyBlockDao().insert(EmergencyBlock(pkg, expiresAt))
                    }
                    Log.d("AppMonitoringService", "Emergency block added for $pkg until $expiresAt.")
                }
                ensureLoopStarted()
                return START_STICKY
            }

            ACTION_START_MONITORING -> {
                Log.d("AppMonitoringService", "Monitoring session started.")
                prefs.edit {
                    putInt("blocked_app_attempts", 0)
                    putBoolean("monitoring_active", true)
                }
                isMonitoringActive = true
                initSessionCounters()
                ensureLoopStarted()
                return START_STICKY
            }

            else -> {
                // null intent (system restart after process death) or ACTION_RESTORE (boot):
                // reconstruct enforcement state from what's persisted, without resetting counters.
                isMonitoringActive = prefs.getBoolean("monitoring_active", false)
                Log.d("AppMonitoringService", "Service restored (monitoring=$isMonitoringActive).")
                ensureLoopStarted()
                if (isMonitoringActive && SessionSettings.sessionStartedAtMs(this) == 0L) {
                    initSessionCounters()
                }
                stopIfNoReason()
                return START_STICKY
            }
        }
    }

    /** Build the foreground notification (idempotent) and start the DB observers + poll loop once. */
    private fun ensureLoopStarted() {
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

        if (isMonitoring) return
        isMonitoring = true

        serviceScope.launch {
            db.blockedAppDao().getAllBlockedApps().collect { list ->
                packageToGroupId = list.associate { it.packageName to it.groupId }
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked apps updated from DB: $packageToGroupId")
            }
        }

        serviceScope.launch {
            db.groupTimeRuleDao().observeAll().collect { rules ->
                timeRulesByGroup = rules.groupBy { it.groupId }
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Time rules updated: ${timeRulesByGroup.mapValues { it.value.size }}")
            }
        }

        serviceScope.launch {
            db.emergencyBlockDao().observeAll().collect { list ->
                emergencyBlocks = list.associate { it.packageName to it.expiresAtMs }
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Emergency blocks updated: $emergencyBlocks")
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

                val now = System.currentTimeMillis()

                // Auto-expire a finished timeout.
                if (SessionSettings.timeoutEndsAtMs(localContext) in 1..now) {
                    SessionSettings.setTimeoutEndsAtMs(localContext, 0L)
                }
                // Purge expired emergency rows (the Flow will refresh the cached map).
                if (emergencyBlocks.any { it.value <= now }) {
                    serviceScope.launch { db.emergencyBlockDao().deleteExpired(now) }
                }
                // Nothing left to enforce → stop.
                if (stopIfNoReason()) break

                val foreground = getForegroundInfo()
                val foregroundApp = foreground.packageName
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Foreground: $foregroundApp / ${foreground.className}")

                val timeoutActive = SessionSettings.timeoutActive(localContext, now)
                val allowed = if (timeoutActive) {
                    TimeoutPolicy.allowedPackages(packageToGroupId, SessionSettings.timeoutAllowedGroupId(localContext))
                } else emptySet()

                if (foregroundApp != null && foregroundApp != packageName &&
                    TimeoutPolicy.isEmergencyBlocked(foregroundApp, emergencyBlocks, now)
                ) {
                    launchBlock(localContext, foregroundApp, groupId = null, mode = BLOCK_MODE_EMERGENCY)
                } else if (timeoutActive &&
                    TimeoutPolicy.shouldBlockInTimeout(foregroundApp, packageName, allowed, CriticalApps.PACKAGES)
                ) {
                    launchBlock(localContext, foregroundApp!!, groupId = null, mode = BLOCK_MODE_TIMEOUT)
                } else if (isMonitoringActive && !isBreakActive &&
                    foregroundApp != null && packageToGroupId.containsKey(foregroundApp) && foregroundApp != packageName
                ) {
                    val groupId = packageToGroupId[foregroundApp]
                    if (TimeoutPolicy.isAlwaysAllowed(groupId, SessionSettings.timeoutAllowedGroupId(localContext))) {
                        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Skipping block for $foregroundApp: in always-allowed Timeout group.")
                        delay(1000)
                        continue
                    }
                    if (groupId != null && !effectiveSettings(groupId).blockingEnabled) {
                        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Skipping block for $foregroundApp: group=$groupId disabled by time rule.")
                        delay(1000)
                        continue
                    }
                    launchBlock(localContext, foregroundApp, groupId, mode = BLOCK_MODE_NORMAL)
                } else if (isMonitoringActive && isDeviceAdminActive() && isUninstallPath(foreground)) {
                    // Anti-uninstall protection applies only to a normal monitoring session — not
                    // when the service is alive purely for a Timeout or Emergency block.
                    val home = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(home)
                    if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Uninstall path detected (${foreground.packageName}/${foreground.className}) — redirecting home.")
                }
                delay(1000)
            }
        }
    }

    private fun launchBlock(context: Context, packageName: String, groupId: Long?, mode: String) {
        val blockIntent = Intent(context, BlockingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_BLOCKED_APP_PACKAGE_NAME, packageName)
            putExtra(EXTRA_BLOCK_MODE, mode)
            if (groupId != null) putExtra(EXTRA_BLOCKED_GROUP_ID, groupId)
        }
        startActivity(blockIntent)
        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked $packageName (mode=$mode group=$groupId)")
        val attempts = prefs.getInt("blocked_app_attempts", 0)
        prefs.edit { putInt("blocked_app_attempts", attempts + 1) }
    }

    private fun hasActiveEmergency(nowMs: Long): Boolean = emergencyBlocks.any { it.value > nowMs }

    /** Stop the service if no enforcement reason remains. Returns true if it stopped. */
    private fun stopIfNoReason(): Boolean {
        val now = System.currentTimeMillis()
        if (!isMonitoringActive && !SessionSettings.timeoutActive(this, now) && !hasActiveEmergency(now)) {
            Log.d("AppMonitoringService", "No enforcement reason left — stopping service.")
            stopSelf()
            return true
        }
        return false
    }

    /**
     * Wipe stale per-group session state and initialize fresh counters for every existing group
     * (plus the ungrouped bucket). Each group's break budget is its override or the global default.
     */
    private fun initSessionCounters() {
        SessionSettings.clearAllGroupSessionState(this)
        SessionSettings.setSessionStartedAtMs(this, System.currentTimeMillis())
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
                    override fun onFinish() = finishBreak()
                }.start()
            }
        }
    }

    private fun finishBreak() {
        isBreakActive = false
        val finishedGroupId = currentBreakGroupId
        currentBreakGroupId = null
        SessionSettings.setGroupLastBreakEndedAtMs(
            this,
            finishedGroupId,
            System.currentTimeMillis()
        )
        Log.d("AppMonitoringService", "Break finished (group=$finishedGroupId).")
    }

    private suspend fun effectiveBreakDurationMs(groupId: Long?): Long {
        val global = SessionSettings.breakDurationMs(this)
        if (groupId == null) return global
        val group = db.appGroupDao().getById(groupId)
        val rules = timeRulesByGroup[groupId].orEmpty()
        val now = System.currentTimeMillis()
        return resolveForGroup(
            group = group,
            rules = rules,
            nowDayOfWeekBit = currentDayOfWeekBit(now),
            nowMinuteOfDay = currentMinuteOfDay(now),
            global = GlobalBreakSettings(
                durationMs = global,
                count = SessionSettings.breakCount(this),
                minBetweenMs = SessionSettings.minBetweenBreaksMs(this)
            )
        ).durationMs
    }

    /**
     * Resolve settings for the polling loop's "should this app block right now?" decision.
     * Only needs blockingEnabled; uses the cached rules and skips a DB lookup for the group
     * (group static overrides don't affect blockingEnabled — that comes from rules alone).
     */
    private fun effectiveSettings(groupId: Long): ResolvedGroupSettings {
        val rules = timeRulesByGroup[groupId].orEmpty()
        val now = System.currentTimeMillis()
        return resolveForGroup(
            group = null,
            rules = rules,
            nowDayOfWeekBit = currentDayOfWeekBit(now),
            nowMinuteOfDay = currentMinuteOfDay(now),
            global = GlobalBreakSettings(
                durationMs = SessionSettings.breakDurationMs(this),
                count = SessionSettings.breakCount(this),
                minBetweenMs = SessionSettings.minBetweenBreaksMs(this)
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isMonitoringActive = false
        prefs.edit { putBoolean("monitoring_active", false) }
        SessionSettings.setTimeoutEndsAtMs(this, 0L)
        SessionSettings.clearAllGroupSessionState(this)
        // Emergency blocks intentionally persist in the DB across service death (24h window).
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
