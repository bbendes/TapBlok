package com.cj.tapblok.settings

import com.cj.tapblok.database.AppGroup
import com.cj.tapblok.database.GroupTimeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveForGroupTest {

    private val global = GlobalBreakSettings(
        durationMs = 300_000L,
        count = 3,
        minBetweenMs = 60_000L
    )

    private fun rule(
        id: Long = 1,
        days: Int = 0b1111111,
        start: Int = 0,
        end: Int = 1440,
        priority: Int = 0,
        blocking: Boolean = true,
        count: Int? = null,
        duration: Long? = null,
        cooldown: Long? = null
    ) = GroupTimeRule(
        id = id, groupId = 1, daysOfWeekMask = days,
        startMinuteOfDay = start, endMinuteOfDay = end,
        priority = priority, blockingEnabled = blocking,
        breakCountOverride = count,
        breakDurationMsOverride = duration,
        minBetweenBreaksMsOverride = cooldown
    )

    @Test
    fun `no rules falls through to group then global`() {
        val group = AppGroup(id = 1, name = "X", breakCount = 7)
        val out = resolveForGroup(group, emptyList(), 0, 600, global)
        assertEquals(7, out.count)
        assertEquals(global.durationMs, out.durationMs)
        assertTrue(out.blockingEnabled)
        assertNull(out.matchedRuleId)
    }

    @Test
    fun `rule outside window does not match`() {
        val rules = listOf(rule(start = 600, end = 1080, count = 1))
        val out = resolveForGroup(null, rules, 0, 300, global)
        assertEquals(global.count, out.count)
        assertNull(out.matchedRuleId)
    }

    @Test
    fun `rule inside window applies overrides`() {
        val rules = listOf(rule(start = 600, end = 1080, count = 1, duration = 90_000L))
        val out = resolveForGroup(null, rules, 0, 700, global)
        assertEquals(1, out.count)
        assertEquals(90_000L, out.durationMs)
        assertEquals(1L, out.matchedRuleId)
    }

    @Test
    fun `rule disables blocking`() {
        val rules = listOf(rule(blocking = false))
        val out = resolveForGroup(null, rules, 0, 12, global)
        assertFalse(out.blockingEnabled)
    }

    @Test
    fun `midnight span window matches both sides`() {
        val rules = listOf(rule(start = 1320, end = 360, count = 5)) // 22:00 -> 06:00
        // 23:00
        assertEquals(5, resolveForGroup(null, rules, 0, 23 * 60, global).count)
        // 02:00
        assertEquals(5, resolveForGroup(null, rules, 0, 2 * 60, global).count)
        // 12:00 — outside
        assertEquals(global.count, resolveForGroup(null, rules, 0, 12 * 60, global).count)
    }

    @Test
    fun `day-of-week mask filters`() {
        val mondayOnly = listOf(rule(days = 0b0000001, count = 9))
        // Monday (bit 0)
        assertEquals(9, resolveForGroup(null, mondayOnly, 0, 600, global).count)
        // Tuesday (bit 1)
        assertEquals(global.count, resolveForGroup(null, mondayOnly, 1, 600, global).count)
    }

    @Test
    fun `priority then id breaks ties`() {
        val rules = listOf(
            rule(id = 1, priority = 5, count = 1),
            rule(id = 2, priority = 0, count = 2),
            rule(id = 3, priority = 0, count = 3),
        )
        val out = resolveForGroup(null, rules, 0, 600, global)
        // Lowest priority (0) wins; among priority 0 the lowest id (2).
        assertEquals(2, out.count)
        assertEquals(2L, out.matchedRuleId)
    }

    @Test
    fun `rule override beats group override beats global`() {
        val group = AppGroup(id = 1, name = "G", breakCount = 4)
        val rules = listOf(rule(count = 9))
        val out = resolveForGroup(group, rules, 0, 600, global)
        assertEquals(9, out.count) // rule wins
        // Same setup but rule has no override -> group static used.
        val out2 = resolveForGroup(group, listOf(rule(count = null)), 0, 600, global)
        assertEquals(4, out2.count)
    }
}
