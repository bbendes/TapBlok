package com.cj.tapblok.settings

import android.content.Context
import androidx.core.content.edit

object SessionSettings {
    private const val PREFS = "app_prefs"

    const val KEY_BREAK_DURATION_MS = "break_duration_ms"
    const val KEY_BREAK_COUNT = "break_count"
    const val KEY_MIN_BETWEEN_BREAKS_MS = "min_between_breaks_ms"
    const val KEY_MIN_DELAY_BEFORE_FIRST_BREAK_MS = "min_delay_before_first_break_ms"
    const val KEY_REQUIRE_NFC_BREAK_TAG = "require_nfc_break_tag"
    const val KEY_START_TAG_ENDS_BREAK = "start_tag_ends_break"
    const val KEY_LAST_BREAK_ENDED_AT_MS = "last_break_ended_at_ms"
    const val KEY_SESSION_STARTED_AT_MS = "session_started_at_ms"

    private const val GROUP_BREAKS_REMAINING_PREFIX = "breaks_remaining_g_"
    private const val GROUP_LAST_BREAK_ENDED_AT_PREFIX = "last_break_ended_at_g_"

    private fun groupKeySuffix(groupId: Long?): String = groupId?.toString() ?: "null"

    fun breaksRemainingKey(groupId: Long?): String = GROUP_BREAKS_REMAINING_PREFIX + groupKeySuffix(groupId)
    fun lastBreakEndedAtKey(groupId: Long?): String = GROUP_LAST_BREAK_ENDED_AT_PREFIX + groupKeySuffix(groupId)

    fun breaksRemaining(context: Context, groupId: Long?): Int =
        prefs(context).getInt(breaksRemainingKey(groupId), 0)

    fun setBreaksRemaining(context: Context, groupId: Long?, value: Int) =
        prefs(context).edit { putInt(breaksRemainingKey(groupId), value) }

    fun groupLastBreakEndedAtMs(context: Context, groupId: Long?): Long =
        prefs(context).getLong(lastBreakEndedAtKey(groupId), 0L)

    fun setGroupLastBreakEndedAtMs(context: Context, groupId: Long?, value: Long) =
        prefs(context).edit { putLong(lastBreakEndedAtKey(groupId), value) }

    fun clearAllGroupSessionState(context: Context) {
        val editor = prefs(context).edit()
        for ((key, _) in prefs(context).all) {
            if (key.startsWith(GROUP_BREAKS_REMAINING_PREFIX) ||
                key.startsWith(GROUP_LAST_BREAK_ENDED_AT_PREFIX)
            ) {
                editor.remove(key)
            }
        }
        editor.remove(KEY_SESSION_STARTED_AT_MS)
        editor.apply()
    }

    const val DEFAULT_BREAK_DURATION_MS = 5L * 60_000L
    const val DEFAULT_BREAK_COUNT = 3
    const val DEFAULT_MIN_BETWEEN_BREAKS_MS = 0L
    const val DEFAULT_MIN_DELAY_BEFORE_FIRST_BREAK_MS = 0L
    const val DEFAULT_REQUIRE_NFC_BREAK_TAG = false
    const val DEFAULT_START_TAG_ENDS_BREAK = false

    const val MAX_BREAK_DURATION_MS = 30L * 60_000L
    const val MAX_BREAK_COUNT = 10
    const val MAX_MIN_BETWEEN_BREAKS_MS = 60L * 60_000L
    const val MAX_MIN_DELAY_BEFORE_FIRST_BREAK_MS = 60L * 60_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun breakDurationMs(context: Context): Long =
        prefs(context).getLong(KEY_BREAK_DURATION_MS, DEFAULT_BREAK_DURATION_MS)

    fun setBreakDurationMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_BREAK_DURATION_MS, value) }

    fun breakCount(context: Context): Int =
        prefs(context).getInt(KEY_BREAK_COUNT, DEFAULT_BREAK_COUNT)

    fun setBreakCount(context: Context, value: Int) =
        prefs(context).edit { putInt(KEY_BREAK_COUNT, value) }

    fun minBetweenBreaksMs(context: Context): Long =
        prefs(context).getLong(KEY_MIN_BETWEEN_BREAKS_MS, DEFAULT_MIN_BETWEEN_BREAKS_MS)

    fun setMinBetweenBreaksMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_MIN_BETWEEN_BREAKS_MS, value) }

    fun minDelayBeforeFirstBreakMs(context: Context): Long =
        prefs(context).getLong(KEY_MIN_DELAY_BEFORE_FIRST_BREAK_MS, DEFAULT_MIN_DELAY_BEFORE_FIRST_BREAK_MS)

    fun setMinDelayBeforeFirstBreakMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_MIN_DELAY_BEFORE_FIRST_BREAK_MS, value) }

    fun sessionStartedAtMs(context: Context): Long =
        prefs(context).getLong(KEY_SESSION_STARTED_AT_MS, 0L)

    fun setSessionStartedAtMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_SESSION_STARTED_AT_MS, value) }

    fun requireNfcBreakTag(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_NFC_BREAK_TAG, DEFAULT_REQUIRE_NFC_BREAK_TAG)

    fun setRequireNfcBreakTag(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_REQUIRE_NFC_BREAK_TAG, value) }

    fun startTagEndsBreak(context: Context): Boolean =
        prefs(context).getBoolean(KEY_START_TAG_ENDS_BREAK, DEFAULT_START_TAG_ENDS_BREAK)

    fun setStartTagEndsBreak(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_START_TAG_ENDS_BREAK, value) }

    fun lastBreakEndedAtMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BREAK_ENDED_AT_MS, 0L)

    fun setLastBreakEndedAtMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_LAST_BREAK_ENDED_AT_MS, value) }

    fun canTakeBreak(
        nowMs: Long,
        lastBreakEndedAtMs: Long,
        minBetweenBreaksMs: Long,
        sessionStartedAtMs: Long = 0L,
        minDelayBeforeFirstBreakMs: Long = 0L,
    ): Boolean = nextBreakAvailableInMs(
        nowMs, lastBreakEndedAtMs, minBetweenBreaksMs, sessionStartedAtMs, minDelayBeforeFirstBreakMs
    ) == 0L

    fun nextBreakAvailableInMs(
        nowMs: Long,
        lastBreakEndedAtMs: Long,
        minBetweenBreaksMs: Long,
        sessionStartedAtMs: Long = 0L,
        minDelayBeforeFirstBreakMs: Long = 0L,
    ): Long {
        val cooldownRemaining = if (lastBreakEndedAtMs == 0L) 0L
            else ((lastBreakEndedAtMs + minBetweenBreaksMs) - nowMs).coerceAtLeast(0L)
        val startDelayRemaining = if (sessionStartedAtMs == 0L || minDelayBeforeFirstBreakMs == 0L) 0L
            else ((sessionStartedAtMs + minDelayBeforeFirstBreakMs) - nowMs).coerceAtLeast(0L)
        return maxOf(cooldownRemaining, startDelayRemaining)
    }
}
