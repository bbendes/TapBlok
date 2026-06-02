package com.cj.tapblok

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cj.tapblok.nfc.NfcTagType
import com.cj.tapblok.ui.theme.TapBlokTheme
import java.io.IOException

class NfcWriteActivity : ComponentActivity() {

    companion object {
        const val NFC_MIME_TYPE = "application/vnd.com.cj.tapblok"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var ndefMessage: NdefMessage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            TapBlokTheme {
                var selectedType by remember { mutableStateOf<NfcTagType?>(null) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (selectedType == null) {
                        TagTypePicker(onSelect = {
                            selectedType = it
                            ndefMessage = createNdefMessage(it.payload)
                        })
                    } else {
                        ReadyToWriteScreen(tagType = selectedType!!)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch to give this activity priority for NFC intents
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch when the activity is not in the foreground
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // This method is called when an NFC tag is detected while the activity is in the foreground
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtraCompat<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null && ndefMessage != null) {
            writeNdefMessageToTag(ndefMessage!!, tag)
            finish()
        }
    }

    private fun createNdefMessage(payload: String): NdefMessage {
        val mimeType = NFC_MIME_TYPE
        val mimeRecord = NdefRecord.createMime(mimeType, payload.toByteArray(Charsets.UTF_8))
        return NdefMessage(arrayOf(mimeRecord))
    }

    private fun writeNdefMessageToTag(message: NdefMessage, tag: Tag) {
        val ndef = Ndef.get(tag)
        ndef?.use {
            try {
                it.connect()
                val messageBytes = message.toByteArray()
                if (it.maxSize < messageBytes.size) {
                    Toast.makeText(this, "Tag is too small!", Toast.LENGTH_SHORT).show()
                    return
                }
                if (!it.isWritable) {
                    Toast.makeText(this, "Tag is read-only!", Toast.LENGTH_SHORT).show()
                    return
                }
                it.writeNdefMessage(message)
                Toast.makeText(this, "Tag written successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("NfcWriteActivity", "Error writing NFC tag", e)
                Toast.makeText(this, "Failed to write tag.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TagTypePicker(onSelect: (NfcTagType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose tag type",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Pick how this tag behaves when scanned.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TagTypeCard(
            icon = Icons.Default.SyncAlt,
            title = "Start & Stop",
            subtitle = "Toggles a session on or off. Hide this tag somewhere inconvenient.",
            onClick = { onSelect(NfcTagType.Toggle) }
        )
        TagTypeCard(
            icon = Icons.Default.PlayArrow,
            title = "Start only",
            subtitle = "Only starts a session — never stops one. Safe to keep handy.",
            onClick = { onSelect(NfcTagType.StartOnly) }
        )
        TagTypeCard(
            icon = Icons.Default.FreeBreakfast,
            title = "Break",
            subtitle = "Starts a break during an active session. Hide somewhere mildly inconvenient.",
            onClick = { onSelect(NfcTagType.Break) }
        )
    }
}

@androidx.compose.runtime.Composable
private fun TagTypeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ReadyToWriteScreen(tagType: NfcTagType) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Ready to Write",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (tagType) {
                NfcTagType.Toggle -> "Start & Stop tag"
                NfcTagType.StartOnly -> "Start-only tag"
                NfcTagType.Break -> "Break tag"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Hold your NFC tag against the back of your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}