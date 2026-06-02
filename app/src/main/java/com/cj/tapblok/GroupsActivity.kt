package com.cj.tapblok

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cj.tapblok.database.AppGroup
import com.cj.tapblok.database.AppGroupDao
import com.cj.tapblok.settings.SessionSettings
import com.cj.tapblok.ui.OverrideSliderRow
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupsViewModel(private val dao: AppGroupDao) : ViewModel() {
    private val _groups = MutableStateFlow<List<AppGroup>>(emptyList())
    val groups: StateFlow<List<AppGroup>> = _groups

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAll().collect { _groups.value = it }
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(AppGroup(name = name.trim()))
        }
    }

    fun rename(group: AppGroup, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(group.copy(name = newName.trim()))
        }
    }

    fun update(group: AppGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(group)
        }
    }

    fun delete(group: AppGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(group)
        }
    }
}

class GroupsViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GroupsViewModel((app as App).database.appGroupDao()) as T
    }
}

class GroupsActivity : ComponentActivity() {
    private val viewModel: GroupsViewModel by viewModels { GroupsViewModelFactory(application) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                val groups by viewModel.groups.collectAsState()
                var showNewDialog by remember { mutableStateOf(false) }
                var renameTarget by remember { mutableStateOf<AppGroup?>(null) }
                var pendingDelete by remember { mutableStateOf<AppGroup?>(null) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Groups") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showNewDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New group")
                        }
                    }
                ) { padding ->
                    GroupsScreen(
                        groups = groups,
                        onUpdate = viewModel::update,
                        onRenameRequest = { renameTarget = it },
                        onDeleteRequest = { pendingDelete = it },
                        modifier = Modifier.padding(padding)
                    )
                }

                if (showNewDialog) {
                    NameDialog(
                        title = "New group",
                        initialValue = "",
                        onConfirm = {
                            viewModel.create(it)
                            showNewDialog = false
                        },
                        onDismiss = { showNewDialog = false }
                    )
                }
                renameTarget?.let { target ->
                    NameDialog(
                        title = "Rename group",
                        initialValue = target.name,
                        onConfirm = {
                            viewModel.rename(target, it)
                            renameTarget = null
                        },
                        onDismiss = { renameTarget = null }
                    )
                }
                pendingDelete?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete group?") },
                        text = { Text("\"${target.name}\" will be removed. Apps in this group revert to using global break settings.") },
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

@Composable
fun GroupsScreen(
    groups: List<AppGroup>,
    onUpdate: (AppGroup) -> Unit,
    onRenameRequest: (AppGroup) -> Unit,
    onDeleteRequest: (AppGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sessionActive by remember { mutableStateOf(AppMonitoringService.isRunning) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionActive = AppMonitoringService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (groups.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No groups yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to create a group. Each group can have its own break settings, applied to any blocked app assigned to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                editable = !sessionActive,
                globalBreakDurationMs = SessionSettings.breakDurationMs(context),
                globalBreakCount = SessionSettings.breakCount(context),
                globalMinBetweenMs = SessionSettings.minBetweenBreaksMs(context),
                globalMinDelayBeforeFirstMs = SessionSettings.minDelayBeforeFirstBreakMs(context),
                onUpdate = onUpdate,
                onRename = { onRenameRequest(group) },
                onDelete = { onDeleteRequest(group) }
            )
        }
        item {
            if (sessionActive) {
                Text(
                    text = "Stop the current session to edit groups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: AppGroup,
    editable: Boolean,
    globalBreakDurationMs: Long,
    globalBreakCount: Int,
    globalMinBetweenMs: Long,
    globalMinDelayBeforeFirstMs: Long,
    onUpdate: (AppGroup) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val intent = Intent(context, GroupTimeRulesActivity::class.java).apply {
                        putExtra(GroupTimeRulesActivity.EXTRA_GROUP_ID, group.id)
                        putExtra(GroupTimeRulesActivity.EXTRA_GROUP_NAME, group.name)
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Schedule, contentDescription = "Time rules")
                }
                IconButton(onClick = onRename, enabled = editable) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = onDelete, enabled = editable) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OverrideSliderRow(
                label = "Break duration",
                overrideValue = group.breakDurationMs?.let { (it / 60_000L).toInt() },
                globalValueDisplay = "${globalBreakDurationMs / 60_000L} min",
                rangeMax = (SessionSettings.MAX_BREAK_DURATION_MS / 60_000L).toInt(),
                stepUnitLabel = "min",
                editable = editable,
                onChange = { newMinutes ->
                    onUpdate(group.copy(breakDurationMs = newMinutes?.toLong()?.times(60_000L)))
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OverrideSliderRow(
                label = "Breaks per session",
                overrideValue = group.breakCount,
                globalValueDisplay = globalBreakCount.toString(),
                rangeMax = SessionSettings.MAX_BREAK_COUNT,
                stepUnitLabel = "",
                rangeMin = 0,
                editable = editable,
                onChange = { onUpdate(group.copy(breakCount = it)) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OverrideSliderRow(
                label = "Cooldown between breaks",
                overrideValue = group.minBetweenBreaksMs?.let { (it / 60_000L).toInt() },
                globalValueDisplay = if (globalMinBetweenMs == 0L) "Off" else "${globalMinBetweenMs / 60_000L} min",
                rangeMax = (SessionSettings.MAX_MIN_BETWEEN_BREAKS_MS / 60_000L).toInt(),
                stepUnitLabel = "min",
                rangeMin = 0,
                editable = editable,
                onChange = { onUpdate(group.copy(minBetweenBreaksMs = it?.toLong()?.times(60_000L))) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OverrideSliderRow(
                label = "Delay before first break",
                overrideValue = group.minDelayBeforeFirstBreakMs?.let { (it / 60_000L).toInt() },
                globalValueDisplay = if (globalMinDelayBeforeFirstMs == 0L) "Off" else "${globalMinDelayBeforeFirstMs / 60_000L} min",
                rangeMax = (SessionSettings.MAX_MIN_DELAY_BEFORE_FIRST_BREAK_MS / 60_000L).toInt(),
                stepUnitLabel = "min",
                rangeMin = 0,
                editable = editable,
                onChange = { onUpdate(group.copy(minDelayBeforeFirstBreakMs = it?.toLong()?.times(60_000L))) }
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
