package com.cj.tapblok

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.settings.GlobalBreakSettings
import com.cj.tapblok.settings.SessionSettings
import com.cj.tapblok.settings.currentDayOfWeekBit
import com.cj.tapblok.settings.currentMinuteOfDay
import com.cj.tapblok.settings.resolveForGroup
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun formatDuration(totalSec: Long): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(AppMonitoringService.EXTRA_BLOCKED_APP_PACKAGE_NAME) ?: "An app"
        val groupId: Long? = if (intent.hasExtra(AppMonitoringService.EXTRA_BLOCKED_GROUP_ID)) {
            intent.getLongExtra(AppMonitoringService.EXTRA_BLOCKED_GROUP_ID, -1L)
        } else null

        val goHome = {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })

        setContent {
            TapBlokTheme {
                BlockingScreen(
                    packageName = packageName,
                    groupId = groupId,
                    onGoHomeClick = goHome,
                    onTakeBreakClick = {
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                            if (groupId != null) {
                                putExtra(AppMonitoringService.EXTRA_BREAK_GROUP_ID, groupId)
                            }
                        }
                        startService(breakIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    groupId: Long?,
    onGoHomeClick: () -> Unit,
    onTakeBreakClick: () -> Unit
) {
    val context = LocalContext.current

    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var breaksRemaining by remember { mutableStateOf(SessionSettings.breaksRemaining(context, groupId)) }
    var cooldownRemainingSec by remember { mutableStateOf(0L) }

    LaunchedEffect(key1 = Unit) {
        val pm = context.packageManager
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            appName = packageName
        }
    }

    LaunchedEffect(key1 = groupId) {
        val global = GlobalBreakSettings(
            durationMs = SessionSettings.breakDurationMs(context),
            count = SessionSettings.breakCount(context),
            minBetweenMs = SessionSettings.minBetweenBreaksMs(context),
            minDelayBeforeFirstBreakMs = SessionSettings.minDelayBeforeFirstBreakMs(context),
        )
        val db = AppDatabase.getDatabase(context)
        val group = if (groupId != null) {
            withContext(Dispatchers.IO) { db.appGroupDao().getById(groupId) }
        } else null
        val rules = if (groupId != null) {
            withContext(Dispatchers.IO) { db.groupTimeRuleDao().getRulesForGroup(groupId) }
        } else emptyList()
        val nowAtResolve = System.currentTimeMillis()
        val effective = resolveForGroup(
            group = group,
            rules = rules,
            nowDayOfWeekBit = currentDayOfWeekBit(nowAtResolve),
            nowMinuteOfDay = currentMinuteOfDay(nowAtResolve),
            global = global
        )
        val lastEndedAtMs = SessionSettings.groupLastBreakEndedAtMs(context, groupId)
        val sessionStartedAtMs = SessionSettings.sessionStartedAtMs(context)

        while (true) {
            val now = System.currentTimeMillis()
            val remainMs = SessionSettings.nextBreakAvailableInMs(
                now,
                lastEndedAtMs,
                effective.minBetweenMs,
                sessionStartedAtMs,
                effective.minDelayBeforeFirstBreakMs,
            )
            cooldownRemainingSec = (remainMs + 999) / 1000
            if (remainMs == 0L) break
            delay(500)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon with lock badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = appIcon),
                        contentDescription = "$appName icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "BLOCKED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tap your NFC tag or scan your QR code to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onGoHomeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go Home")
            }

            if (breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val cooldownActive = cooldownRemainingSec > 0
                OutlinedButton(
                    onClick = {
                        SessionSettings.setBreaksRemaining(context, groupId, breaksRemaining - 1)
                        breaksRemaining -= 1
                        onTakeBreakClick()
                    },
                    enabled = !cooldownActive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (cooldownActive) "Next break in ${formatDuration(cooldownRemainingSec)}"
                        else "Take a Break ($breaksRemaining remaining)"
                    )
                }
            }
        }
    }
}

