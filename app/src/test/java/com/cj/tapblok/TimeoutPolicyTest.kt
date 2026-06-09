package com.cj.tapblok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeoutPolicyTest {

    private val self = "com.cj.tapblok"
    private val critical = setOf("com.android.dialer", "com.android.settings")

    @Test
    fun `allowed packages are not blocked in timeout`() {
        assertFalse(
            TimeoutPolicy.shouldBlockInTimeout("com.allowed.app", self, setOf("com.allowed.app"), critical)
        )
    }

    @Test
    fun `non-allowed app is blocked in timeout`() {
        assertTrue(
            TimeoutPolicy.shouldBlockInTimeout("com.random.app", self, setOf("com.allowed.app"), critical)
        )
    }

    @Test
    fun `critical apps are never blocked in timeout`() {
        assertFalse(
            TimeoutPolicy.shouldBlockInTimeout("com.android.dialer", self, emptySet(), critical)
        )
    }

    @Test
    fun `tapblok itself is never blocked in timeout`() {
        assertFalse(
            TimeoutPolicy.shouldBlockInTimeout(self, self, emptySet(), critical)
        )
    }

    @Test
    fun `null foreground is not blocked in timeout`() {
        assertFalse(
            TimeoutPolicy.shouldBlockInTimeout(null, self, emptySet(), critical)
        )
    }

    @Test
    fun `allowed packages come from the matching group only`() {
        val map = mapOf(
            "com.a" to 5L,
            "com.b" to 5L,
            "com.c" to 9L,
            "com.d" to null,
        )
        assertEquals(setOf("com.a", "com.b"), TimeoutPolicy.allowedPackages(map, 5L))
        assertEquals(setOf("com.c"), TimeoutPolicy.allowedPackages(map, 9L))
    }

    @Test
    fun `no allowed group yields empty allowed set`() {
        val map = mapOf("com.a" to 5L)
        assertEquals(emptySet<String>(), TimeoutPolicy.allowedPackages(map, -1L))
    }

    @Test
    fun `apps in the timeout-allowed group are always allowed`() {
        assertTrue(TimeoutPolicy.isAlwaysAllowed(groupId = 5L, timeoutAllowedGroupId = 5L))
    }

    @Test
    fun `apps in a different group are not always allowed`() {
        assertFalse(TimeoutPolicy.isAlwaysAllowed(groupId = 9L, timeoutAllowedGroupId = 5L))
    }

    @Test
    fun `ungrouped apps are not always allowed`() {
        assertFalse(TimeoutPolicy.isAlwaysAllowed(groupId = null, timeoutAllowedGroupId = 5L))
    }

    @Test
    fun `no allowed group means nothing is always allowed`() {
        assertFalse(TimeoutPolicy.isAlwaysAllowed(groupId = 5L, timeoutAllowedGroupId = -1L))
    }

    @Test
    fun `emergency block in force before expiry`() {
        val blocks = mapOf("com.x" to 2_000L)
        assertTrue(TimeoutPolicy.isEmergencyBlocked("com.x", blocks, nowMs = 1_000L))
    }

    @Test
    fun `emergency block expired at boundary`() {
        val blocks = mapOf("com.x" to 1_000L)
        assertFalse(TimeoutPolicy.isEmergencyBlocked("com.x", blocks, nowMs = 1_000L))
        assertFalse(TimeoutPolicy.isEmergencyBlocked("com.x", blocks, nowMs = 1_001L))
    }

    @Test
    fun `app with no emergency block is not blocked`() {
        assertFalse(TimeoutPolicy.isEmergencyBlocked("com.y", mapOf("com.x" to 9_999L), nowMs = 1L))
    }
}
