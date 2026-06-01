package com.cj.tapblok.settings

import com.cj.tapblok.database.AppGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakSettingsResolverTest {

    private val global = GlobalBreakSettings(
        durationMs = 300_000L,
        count = 3,
        minBetweenMs = 60_000L
    )

    @Test
    fun `null group resolves to global`() {
        val effective = resolveBreakSettings(group = null, global = global)
        assertEquals(global.durationMs, effective.durationMs)
        assertEquals(global.count, effective.count)
        assertEquals(global.minBetweenMs, effective.minBetweenMs)
    }

    @Test
    fun `group with all-null overrides resolves to global`() {
        val group = AppGroup(id = 1, name = "Social", breakDurationMs = null, breakCount = null, minBetweenBreaksMs = null)
        val effective = resolveBreakSettings(group = group, global = global)
        assertEquals(global.durationMs, effective.durationMs)
        assertEquals(global.count, effective.count)
        assertEquals(global.minBetweenMs, effective.minBetweenMs)
    }

    @Test
    fun `group with all overrides resolves to group values`() {
        val group = AppGroup(
            id = 1,
            name = "Social",
            breakDurationMs = 60_000L,
            breakCount = 1,
            minBetweenBreaksMs = 10_000L
        )
        val effective = resolveBreakSettings(group = group, global = global)
        assertEquals(60_000L, effective.durationMs)
        assertEquals(1, effective.count)
        assertEquals(10_000L, effective.minBetweenMs)
    }

    @Test
    fun `partial overrides mix group and global per field`() {
        val group = AppGroup(
            id = 1,
            name = "Mixed",
            breakDurationMs = 120_000L,
            breakCount = null,
            minBetweenBreaksMs = null
        )
        val effective = resolveBreakSettings(group = group, global = global)
        assertEquals(120_000L, effective.durationMs)
        assertEquals(global.count, effective.count)
        assertEquals(global.minBetweenMs, effective.minBetweenMs)
    }
}
