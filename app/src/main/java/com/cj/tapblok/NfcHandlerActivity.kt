package com.cj.tapblok

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cj.tapblok.nfc.NfcTagType
import com.cj.tapblok.settings.SessionSettings

class NfcHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("NfcHandlerActivity", "Activity launched by NFC intent.")
        handleNfcIntent()
    }

    private fun handleNfcIntent() {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val messages = intent.getParcelableArrayExtraCompat<NdefMessage>(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val ndefMessage = messages[0] as NdefMessage
                if (ndefMessage.records.isEmpty()) {
                    Log.w("NfcHandlerActivity", "NFC message has no records.")
                    finish()
                    return
                }
                val record = ndefMessage.records[0]

                if (String(record.type, Charsets.UTF_8) != NfcWriteActivity.NFC_MIME_TYPE) {
                    Log.w("NfcHandlerActivity", "Ignoring NFC tag with unexpected MIME type.")
                    finish()
                    return
                }

                val tagType = NfcTagType.parse(String(record.payload, Charsets.UTF_8))
                Log.d("NfcHandlerActivity", "Valid TapBlok NFC tag detected (type=$tagType).")

                val running = isServiceRunning(this, AppMonitoringService::class.java)
                when (tagType) {
                    NfcTagType.StartOnly -> {
                        if (running) {
                            if (AppMonitoringService.isBreakActive &&
                                SessionSettings.startTagEndsBreak(this)
                            ) {
                                val endIntent = Intent(this, AppMonitoringService::class.java).apply {
                                    action = AppMonitoringService.ACTION_END_BREAK
                                }
                                startService(endIntent)
                                Toast.makeText(this, "Break ended.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Session already active.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            startMonitoringService(this)
                            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    NfcTagType.Toggle -> {
                        // While a Timeout is active, the Toggle tag ends Timeout (sanctioned early-exit)
                        // and leaves the monitoring session untouched.
                        if (SessionSettings.timeoutActive(this, System.currentTimeMillis())) {
                            endTimeout(this)
                            Toast.makeText(this, "Timeout ended.", Toast.LENGTH_SHORT).show()
                        } else if (running) {
                            stopMonitoring(this)
                            Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                        } else {
                            startMonitoringService(this)
                            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    NfcTagType.Break -> {
                        if (running) {
                            val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                                action = AppMonitoringService.ACTION_START_BREAK
                            }
                            startService(breakIntent)
                            Toast.makeText(this, "Break started.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Start a session first.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    NfcTagType.Timeout -> {
                        // Always (re)start a fresh full-duration Timeout. Does not change monitoring state.
                        startTimeout(this)
                        val minutes = SessionSettings.timeoutDurationMs(this) / 60_000L
                        Toast.makeText(this, "Timeout mode started (${minutes} min).", Toast.LENGTH_SHORT).show()
                    }
                    NfcTagType.Emergency -> {
                        val pkg = recentForegroundPackage(this)
                        if (pkg != null && pkg != packageName && pkg !in CriticalApps.PACKAGES) {
                            addEmergencyBlock(this, pkg)
                            Toast.makeText(this, "Blocked $pkg for 24h.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "No app to block.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        // Finish the activity immediately since it has no UI
        finish()
    }
}