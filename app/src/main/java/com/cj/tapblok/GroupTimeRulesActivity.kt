package com.cj.tapblok

import android.app.Application
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cj.tapblok.database.GroupTimeRule
import com.cj.tapblok.database.GroupTimeRuleDao
import com.cj.tapblok.settings.SessionSettings
import com.cj.tapblok.ui.OverrideSliderRow
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupTimeRulesViewModel(
    private val dao: GroupTimeRuleDao,
    private val groupId: Long
) : ViewModel() {
    private val _rules = MutableStateFlow<List<GroupTimeRule>>(emptyList())
    val rules: StateFlow<List<GroupTimeRule>> = _rules

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.observeRulesForGroup(groupId).collect { _rules.value = it }
        }
    }

    fun add(rule: GroupTimeRule) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(rule.copy(groupId = groupId, id = 0))
        }
    }

    fun update(rule: GroupTimeRule) {
        viewModelScope.launch(Dispatchers.IO) { dao.update(rule) }
    }

    fun delete(rule: GroupTimeRule) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(rule) }
    }
}

class GroupTimeRulesViewModelFactory(
    private val app: Application,
    private val groupId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GroupTimeRulesViewModel(
            (app as App).database.groupTimeRuleDao(),
            groupId
        ) as T
    }
}

class GroupTimeRulesActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_GROUP_NAME = "group_name"
    }

    private val groupId: Long by lazy { intent.getLongExtra(EXTRA_GROUP_ID, -1L) }
    private val groupName: String by lazy { intent.getStringExtra(EXTRA_GROUP_NAME) ?: "Group" }

    private val viewModel: GroupTimeRulesViewModel by viewModels {
        GroupTimeRulesViewModelFactory(application, groupId)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (groupId < 0) { finish(); return }
        setContent {
            TapBlokTheme {
                val rules by viewModel.rules.collectAsState()
                var editTarget by remember { mutableStateOf<GroupTimeRule?>(null) }
                var showAddDialog by remember { mutableStateOf(false) }
                var pendingDelete by remember { mutableStateOf<GroupTimeRule?>(null) }
                val sessionActive = AppMonitoringService.isMonitoringActive

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Schedule — $groupName") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add rule")
                        }
                    }
                ) { padding ->
                    RulesScreen(
                        rules = rules,
                        editable = !sessionActive,
                        onEdit = { editTarget = it },
                        onDelete = { pendingDelete = it },
                        modifier = Modifier.padding(padding)
                    )
                }

                if (showAddDialog) {
                    RuleEditorDialog(
                        title = "Add time rule",
                        initial = newRuleTemplate(groupId),
                        onConfirm = {
                            viewModel.add(it)
                            showAddDialog = false
                        },
                        onDismiss = { showAddDialog = false }
                    )
                }
                editTarget?.let { target ->
                    RuleEditorDialog(
                        title = "Edit time rule",
                        initial = target,
                        onConfirm = {
                            viewModel.update(it)
                            editTarget = null
                        },
                        onDismiss = { editTarget = null }
                    )
                }
                pendingDelete?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete time rule?") },
                        text = { Text("This window will no longer override settings for ${groupName}.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.delete(target)
                                pendingDelete = null
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

private fun newRuleTemplate(groupId: Long): GroupTimeRule = GroupTimeRule(
    groupId = groupId,
    daysOfWeekMask = 0b0011111, // Mon-Fri
    startMinuteOfDay = 9 * 60,
    endMinuteOfDay = 17 * 60,
    priority = 0,
    blockingEnabled = true
)

@Composable
private fun RulesScreen(
    rules: List<GroupTimeRule>,
    editable: Boolean,
    onEdit: (GroupTimeRule) -> Unit,
    onDelete: (GroupTimeRule) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No time rules", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a time window to override break settings — or disable blocking entirely — during specific hours and days. Outside any matching window, the group's normal settings apply.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                editable = editable,
                onClick = { if (editable) onEdit(rule) },
                onDelete = { onDelete(rule) }
            )
        }
        if (!editable) {
            item {
                Text(
                    "Stop the current session to edit time rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleCard(
    rule: GroupTimeRule,
    editable: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${formatMinute(rule.startMinuteOfDay)} – ${formatMinute(rule.endMinuteOfDay)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatDayMask(rule.daysOfWeekMask),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, enabled = editable) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete rule")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = ruleSummary(rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ruleSummary(rule: GroupTimeRule): String {
    if (!rule.blockingEnabled) return "Blocking disabled in this window"
    val parts = buildList {
        rule.breakCountOverride?.let { add("$it breaks") }
        rule.breakDurationMsOverride?.let { add("${it / 60_000L} min duration") }
        rule.minBetweenBreaksMsOverride?.let { add("${it / 60_000L} min cooldown") }
        rule.minDelayBeforeFirstBreakMsOverride?.let { add("${it / 60_000L} min start delay") }
    }
    return if (parts.isEmpty()) "Uses group defaults" else parts.joinToString(" • ")
}

private fun formatMinute(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60) % 24
    val m = minuteOfDay % 60
    return "%02d:%02d".format(h, m)
}

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun formatDayMask(mask: Int): String {
    if (mask == 0) return "Never"
    if (mask == 0b1111111) return "Every day"
    if (mask == 0b0011111) return "Weekdays"
    if (mask == 0b1100000) return "Weekends"
    return (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(" ") { DAY_LABELS[it] }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    title: String,
    initial: GroupTimeRule,
    onConfirm: (GroupTimeRule) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        showTimePicker(context, draft.startMinuteOfDay) { mins ->
                            draft = draft.copy(startMinuteOfDay = mins)
                        }
                    }) { Text(formatMinute(draft.startMinuteOfDay)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("End", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        showTimePicker(context, draft.endMinuteOfDay) { mins ->
                            draft = draft.copy(endMinuteOfDay = mins)
                        }
                    }) { Text(formatMinute(draft.endMinuteOfDay)) }
                }
                if (draft.endMinuteOfDay <= draft.startMinuteOfDay) {
                    Text(
                        "Spans midnight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Days", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { i, lbl ->
                        val bit = 1 shl i
                        val selected = (draft.daysOfWeekMask and bit) != 0
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(
                                    daysOfWeekMask = if (selected) draft.daysOfWeekMask and bit.inv()
                                    else draft.daysOfWeekMask or bit
                                )
                            },
                            label = { Text(lbl) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blocking enabled", modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.blockingEnabled,
                        onCheckedChange = { draft = draft.copy(blockingEnabled = it) }
                    )
                }
                if (draft.blockingEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OverrideSliderRow(
                        label = "Break duration",
                        overrideValue = draft.breakDurationMsOverride?.let { (it / 60_000L).toInt() },
                        globalValueDisplay = "group default",
                        rangeMax = (SessionSettings.MAX_BREAK_DURATION_MS / 60_000L).toInt(),
                        stepUnitLabel = "min",
                        editable = true,
                        onChange = { mins ->
                            draft = draft.copy(breakDurationMsOverride = mins?.toLong()?.times(60_000L))
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    OverrideSliderRow(
                        label = "Breaks in window",
                        overrideValue = draft.breakCountOverride,
                        globalValueDisplay = "group default",
                        rangeMax = SessionSettings.MAX_BREAK_COUNT,
                        stepUnitLabel = "",
                        rangeMin = 0,
                        editable = true,
                        onChange = { draft = draft.copy(breakCountOverride = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OverrideSliderRow(
                        label = "Cooldown",
                        overrideValue = draft.minBetweenBreaksMsOverride?.let { (it / 60_000L).toInt() },
                        globalValueDisplay = "group default",
                        rangeMax = (SessionSettings.MAX_MIN_BETWEEN_BREAKS_MS / 60_000L).toInt(),
                        stepUnitLabel = "min",
                        rangeMin = 0,
                        editable = true,
                        onChange = { mins ->
                            draft = draft.copy(minBetweenBreaksMsOverride = mins?.toLong()?.times(60_000L))
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    OverrideSliderRow(
                        label = "Delay before first break",
                        overrideValue = draft.minDelayBeforeFirstBreakMsOverride?.let { (it / 60_000L).toInt() },
                        globalValueDisplay = "group default",
                        rangeMax = (SessionSettings.MAX_MIN_DELAY_BEFORE_FIRST_BREAK_MS / 60_000L).toInt(),
                        stepUnitLabel = "min",
                        rangeMin = 0,
                        editable = true,
                        onChange = { mins ->
                            draft = draft.copy(minDelayBeforeFirstBreakMsOverride = mins?.toLong()?.times(60_000L))
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = draft.daysOfWeekMask != 0
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun showTimePicker(
    context: android.content.Context,
    initialMinuteOfDay: Int,
    onPicked: (Int) -> Unit
) {
    val initHour = (initialMinuteOfDay / 60) % 24
    val initMin = initialMinuteOfDay % 60
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initHour,
        initMin,
        true
    ).show()
}
