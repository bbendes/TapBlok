package com.cj.tapblok.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakCooldownTest {

    @Test
    fun `no prior break — always allowed`() {
        assertTrue(SessionSettings.canTakeBreak(nowMs = 1_000_000L, lastBreakEndedAtMs = 0L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `cooldown zero — always allowed even just after a break`() {
        assertTrue(SessionSettings.canTakeBreak(nowMs = 1_000L, lastBreakEndedAtMs = 1_000L, minBetweenBreaksMs = 0L))
    }

    @Test
    fun `within cooldown — blocked`() {
        assertFalse(SessionSettings.canTakeBreak(nowMs = 30_000L, lastBreakEndedAtMs = 0L + 1L, minBetweenBreaksMs = 60_000L))
        assertFalse(SessionSettings.canTakeBreak(nowMs = 59_999L, lastBreakEndedAtMs = 0L + 1L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `exactly at cooldown boundary — allowed`() {
        assertTrue(SessionSettings.canTakeBreak(nowMs = 60_001L, lastBreakEndedAtMs = 1L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `past cooldown — allowed`() {
        assertTrue(SessionSettings.canTakeBreak(nowMs = 200_000L, lastBreakEndedAtMs = 100_000L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `next-available — zero when no prior break`() {
        assertEquals(0L, SessionSettings.nextBreakAvailableInMs(nowMs = 1_000_000L, lastBreakEndedAtMs = 0L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `next-available — counts down`() {
        assertEquals(60_000L, SessionSettings.nextBreakAvailableInMs(nowMs = 100_000L, lastBreakEndedAtMs = 100_000L, minBetweenBreaksMs = 60_000L))
        assertEquals(30_000L, SessionSettings.nextBreakAvailableInMs(nowMs = 130_000L, lastBreakEndedAtMs = 100_000L, minBetweenBreaksMs = 60_000L))
        assertEquals(0L, SessionSettings.nextBreakAvailableInMs(nowMs = 160_000L, lastBreakEndedAtMs = 100_000L, minBetweenBreaksMs = 60_000L))
    }

    @Test
    fun `next-available — never negative`() {
        assertEquals(0L, SessionSettings.nextBreakAvailableInMs(nowMs = 9_999_999L, lastBreakEndedAtMs = 100_000L, minBetweenBreaksMs = 60_000L))
    }
}
