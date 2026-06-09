package com.cj.tapblok.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcTagTypeTest {

    @Test
    fun `start payload parses as StartOnly`() {
        assertEquals(NfcTagType.StartOnly, NfcTagType.parse("start"))
    }

    @Test
    fun `toggle payload parses as Toggle`() {
        assertEquals(NfcTagType.Toggle, NfcTagType.parse("toggle"))
    }

    @Test
    fun `legacy work payload parses as Toggle`() {
        assertEquals(NfcTagType.Toggle, NfcTagType.parse("work"))
    }

    @Test
    fun `empty payload parses as Toggle`() {
        assertEquals(NfcTagType.Toggle, NfcTagType.parse(""))
    }

    @Test
    fun `garbage payload parses as Toggle`() {
        assertEquals(NfcTagType.Toggle, NfcTagType.parse("anything-else"))
    }

    @Test
    fun `timeout payload parses as Timeout`() {
        assertEquals(NfcTagType.Timeout, NfcTagType.parse("timeout"))
    }

    @Test
    fun `emergency payload parses as Emergency`() {
        assertEquals(NfcTagType.Emergency, NfcTagType.parse("emergency"))
    }

    @Test
    fun `payload roundtrip for non-legacy types`() {
        assertEquals(NfcTagType.StartOnly, NfcTagType.parse(NfcTagType.StartOnly.payload))
        assertEquals(NfcTagType.Toggle, NfcTagType.parse(NfcTagType.Toggle.payload))
        assertEquals(NfcTagType.Break, NfcTagType.parse(NfcTagType.Break.payload))
        assertEquals(NfcTagType.Timeout, NfcTagType.parse(NfcTagType.Timeout.payload))
        assertEquals(NfcTagType.Emergency, NfcTagType.parse(NfcTagType.Emergency.payload))
    }
}
