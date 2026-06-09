package com.cj.tapblok.nfc

enum class NfcTagType(val payload: String) {
    Toggle("toggle"),
    StartOnly("start"),
    Break("break"),
    Timeout("timeout"),
    Emergency("emergency");

    companion object {
        const val LEGACY_TOGGLE_PAYLOAD = "work"

        fun parse(payload: String): NfcTagType = when (payload) {
            StartOnly.payload -> StartOnly
            Break.payload -> Break
            Timeout.payload -> Timeout
            Emergency.payload -> Emergency
            else -> Toggle
        }
    }
}
