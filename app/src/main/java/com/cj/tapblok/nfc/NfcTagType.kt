package com.cj.tapblok.nfc

enum class NfcTagType(val payload: String) {
    Toggle("toggle"),
    StartOnly("start"),
    Break("break");

    companion object {
        const val LEGACY_TOGGLE_PAYLOAD = "work"

        fun parse(payload: String): NfcTagType = when (payload) {
            StartOnly.payload -> StartOnly
            Break.payload -> Break
            else -> Toggle
        }
    }
}
