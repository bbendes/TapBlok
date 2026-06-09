package com.cj.tapblok

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.database.EmergencyBlock
import com.cj.tapblok.nfc.NfcTagType
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmergencyBlocksActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    // Package whose removal is awaiting a Start & Stop tag scan (null = nothing pending).
    private val pendingRemoval = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            TapBlokTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Emergency blocks") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    EmergencyBlocksScreen(
                        modifier = Modifier.padding(padding),
                        pendingRemoval = pendingRemoval,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        val mimeFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType(NfcWriteActivity.NFC_MIME_TYPE)
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e("EmergencyBlocksActivity", "Bad MIME type for NFC filter", e)
                return
            }
        }
        adapter.enableForegroundDispatch(this, pendingIntent, arrayOf(mimeFilter), null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED != intent.action) return
        val pkg = pendingRemoval.value ?: run {
            Toast.makeText(this, "Select Remove on an app first.", Toast.LENGTH_SHORT).show()
            return
        }
        val messages = intent.getParcelableArrayExtraCompat<NdefMessage>(NfcAdapter.EXTRA_NDEF_MESSAGES)
        val ndefMessage = messages?.firstOrNull() as? NdefMessage ?: return
        val record = ndefMessage.records.firstOrNull() ?: return
        if (String(record.type, Charsets.UTF_8) != NfcWriteActivity.NFC_MIME_TYPE) return

        when (NfcTagType.parse(String(record.payload, Charsets.UTF_8))) {
            NfcTagType.Toggle -> {
                pendingRemoval.value = null
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@EmergencyBlocksActivity)
                            .emergencyBlockDao()
                            .deleteByPackage(pkg)
                    }
                    Toast.makeText(this@EmergencyBlocksActivity, "Block removed.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "Scan a Start & Stop tag to confirm removal.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "expired"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "<1m left"
    }
}

@Composable
private fun EmergencyBlocksScreen(
    modifier: Modifier = Modifier,
    pendingRemoval: MutableState<String?>,
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    val blocks by db.emergencyBlockDao().observeAll().collectAsState(initial = emptyList())

    // Tick once a second so the "time left" labels stay current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Resolve app labels for the current packages.
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(blocks.map { it.packageName }) {
        val pm = context.packageManager
        labels = withContext(Dispatchers.IO) {
            blocks.associate { block ->
                block.packageName to appLabel(pm, block.packageName)
            }
        }
    }

    val active = blocks.filter { it.expiresAtMs > now }.sortedBy { it.expiresAtMs }

    // Clear a stale pending removal if its block is gone.
    LaunchedEffect(active.map { it.packageName }) {
        val pending = pendingRemoval.value
        if (pending != null && active.none { it.packageName == pending }) {
            pendingRemoval.value = null
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Apps blocked by an Emergency tag stay blocked for 24 hours, even when monitoring is off. Removing a block requires scanning a Start & Stop tag.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (active.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No apps are emergency-blocked right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(active, key = { it.packageName }) { block ->
                    EmergencyBlockRow(
                        block = block,
                        label = labels[block.packageName] ?: block.packageName,
                        remainingMs = block.expiresAtMs - now,
                        awaitingTag = pendingRemoval.value == block.packageName,
                        onRequestRemove = { pendingRemoval.value = block.packageName },
                        onCancelRemove = { pendingRemoval.value = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyBlockRow(
    block: EmergencyBlock,
    label: String,
    remainingMs: Long,
    awaitingTag: Boolean,
    onRequestRemove: () -> Unit,
    onCancelRemove: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (awaitingTag) "Tap a Start & Stop tag to confirm removal"
                           else "${block.packageName} · ${formatRemaining(remainingMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (awaitingTag) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (awaitingTag) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onCancelRemove) {
                    Text("Cancel")
                }
            } else {
                TextButton(onClick = onRequestRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove block",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun appLabel(pm: PackageManager, packageName: String): String = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(packageName, 0)
    }
    pm.getApplicationLabel(info).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}
