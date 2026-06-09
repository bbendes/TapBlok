package com.cj.tapblok

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.database.AppGroup
import com.cj.tapblok.settings.SessionSettings
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    SettingsScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sessionActive by remember { mutableStateOf(AppMonitoringService.isMonitoringActive) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionActive = AppMonitoringService.isMonitoringActive
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var breakDurationMin by remember {
        mutableFloatStateOf((SessionSettings.breakDurationMs(context) / 60_000L).toFloat())
    }
    var breakCount by remember {
        mutableFloatStateOf(SessionSettings.breakCount(context).toFloat())
    }
    var minBetweenMin by remember {
        mutableFloatStateOf((SessionSettings.minBetweenBreaksMs(context) / 60_000L).toFloat())
    }
    var minDelayBeforeFirstMin by remember {
        mutableFloatStateOf((SessionSettings.minDelayBeforeFirstBreakMs(context) / 60_000L).toFloat())
    }
    var requireNfcBreakTag by remember {
        mutableStateOf(SessionSettings.requireNfcBreakTag(context))
    }
    var startTagEndsBreak by remember {
        mutableStateOf(SessionSettings.startTagEndsBreak(context))
    }
    var timeoutDurationMin by remember {
        mutableFloatStateOf((SessionSettings.timeoutDurationMs(context) / 60_000L).toFloat())
    }

    LaunchedEffect(timeoutDurationMin) {
        SessionSettings.setTimeoutDurationMs(context, timeoutDurationMin.toLong() * 60_000L)
    }
    LaunchedEffect(breakDurationMin) {
        SessionSettings.setBreakDurationMs(context, breakDurationMin.toLong() * 60_000L)
    }
    LaunchedEffect(breakCount) {
        SessionSettings.setBreakCount(context, breakCount.toInt())
    }
    LaunchedEffect(minBetweenMin) {
        SessionSettings.setMinBetweenBreaksMs(context, minBetweenMin.toLong() * 60_000L)
    }
    LaunchedEffect(minDelayBeforeFirstMin) {
        SessionSettings.setMinDelayBeforeFirstBreakMs(context, minDelayBeforeFirstMin.toLong() * 60_000L)
    }
    LaunchedEffect(requireNfcBreakTag) {
        SessionSettings.setRequireNfcBreakTag(context, requireNfcBreakTag)
    }
    LaunchedEffect(startTagEndsBreak) {
        SessionSettings.setStartTagEndsBreak(context, startTagEndsBreak)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Breaks",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (sessionActive) {
            Text(
                text = "Stop the current session to change these settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        SettingSliderCard(
            label = "Break duration",
            value = "${breakDurationMin.toInt()} min",
            sliderValue = breakDurationMin,
            onSliderChange = { breakDurationMin = it },
            valueRange = 1f..(SessionSettings.MAX_BREAK_DURATION_MS / 60_000L).toFloat(),
            steps = (SessionSettings.MAX_BREAK_DURATION_MS / 60_000L - 1).toInt() - 1,
            enabled = !sessionActive
        )

        SettingSliderCard(
            label = "Breaks per session",
            value = breakCount.toInt().toString(),
            sliderValue = breakCount,
            onSliderChange = { breakCount = it },
            valueRange = 0f..SessionSettings.MAX_BREAK_COUNT.toFloat(),
            steps = SessionSettings.MAX_BREAK_COUNT - 1,
            enabled = !sessionActive
        )

        SettingSliderCard(
            label = "Cooldown between breaks",
            value = if (minBetweenMin.toInt() == 0) "Off" else "${minBetweenMin.toInt()} min",
            sliderValue = minBetweenMin,
            onSliderChange = { minBetweenMin = it },
            valueRange = 0f..(SessionSettings.MAX_MIN_BETWEEN_BREAKS_MS / 60_000L).toFloat(),
            steps = (SessionSettings.MAX_MIN_BETWEEN_BREAKS_MS / 60_000L).toInt() - 1,
            enabled = !sessionActive
        )

        SettingSliderCard(
            label = "Delay before first break",
            value = if (minDelayBeforeFirstMin.toInt() == 0) "Off" else "${minDelayBeforeFirstMin.toInt()} min",
            sliderValue = minDelayBeforeFirstMin,
            onSliderChange = { minDelayBeforeFirstMin = it },
            valueRange = 0f..(SessionSettings.MAX_MIN_DELAY_BEFORE_FIRST_BREAK_MS / 60_000L).toFloat(),
            steps = (SessionSettings.MAX_MIN_DELAY_BEFORE_FIRST_BREAK_MS / 60_000L).toInt() - 1,
            enabled = !sessionActive
        )

        SettingSwitchCard(
            label = "Require NFC tag to take a break",
            description = "Hides the Take a Break button — only a Break NFC tag can start a break.",
            checked = requireNfcBreakTag,
            onCheckedChange = { requireNfcBreakTag = it },
            enabled = !sessionActive
        )

        SettingSwitchCard(
            label = "Start tag ends an active break",
            description = "Tap a Start tag during a break to end it early. The cooldown begins immediately.",
            checked = startTagEndsBreak,
            onCheckedChange = { startTagEndsBreak = it },
            enabled = !sessionActive
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Groups",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(context, GroupsActivity::class.java)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GroupWork,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Manage groups",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Timeout mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "A Timeout tag blocks everything except the apps in the allowed group, for the duration below. It does not change your monitoring session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingSliderCard(
            label = "Timeout duration",
            value = "${timeoutDurationMin.toInt()} min",
            sliderValue = timeoutDurationMin,
            onSliderChange = { timeoutDurationMin = it },
            valueRange = 5f..(SessionSettings.MAX_TIMEOUT_DURATION_MS / 60_000L).toFloat(),
            steps = ((SessionSettings.MAX_TIMEOUT_DURATION_MS / 60_000L).toInt() - 5) / 5 - 1,
            enabled = true
        )

        TimeoutAllowedGroupCard()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Protection",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        DeviceAdminCard(enabled = !sessionActive)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceAdminCard(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val component = remember { ComponentName(context, TapBlokDeviceAdminReceiver::class.java) }

    var adminActive by remember { mutableStateOf(dpm.isAdminActive(component)) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminActive = dpm.isAdminActive(component)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adminActive = dpm.isAdminActive(component)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Lock app uninstall", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Greys out Force Stop and bounces you home if you open Android Settings during a session, blocking the uninstall path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = adminActive,
                enabled = enabled,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Lets TapBlok keep itself running during a focus session. Force Stop is greyed out while this is on; you can disable it any time from this screen."
                            )
                        }
                        adminLauncher.launch(intent)
                    } else if (adminActive) {
                        dpm.removeActiveAdmin(component)
                        adminActive = false
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeoutAllowedGroupCard() {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<AppGroup>>(emptyList()) }
    var selectedGroupId by remember {
        mutableLongStateOf(SessionSettings.timeoutAllowedGroupId(context))
    }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).appGroupDao().getAllList()
        }
    }

    val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "None"

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Allowed apps group", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "During Timeout mode, only apps assigned to this group stay usable. These apps are always allowed — they are never blocked, even during a normal session. Assign apps to a group from Manage groups / blocked apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedGroupId = SessionSettings.NO_TIMEOUT_GROUP_ID
                            SessionSettings.setTimeoutAllowedGroupId(context, selectedGroupId)
                            expanded = false
                        }
                    )
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                selectedGroupId = group.id
                                SessionSettings.setTimeoutAllowedGroupId(context, group.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    ElevatedCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SettingSliderCard(
    label: String,
    value: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled
            )
        }
    }
}
