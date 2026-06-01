package com.cj.tapblok.settings

import android.content.Context
import androidx.core.content.edit

object SessionSettings {
    private const val PREFS = "app_prefs"

    const val KEY_BREAK_DURATION_MS = "break_duration_ms"
    const val KEY_BREAK_COUNT = "break_count"
    const val KEY_MIN_BETWEEN_BREAKS_MS = "min_between_breaks_ms"
    const val KEY_LAST_BREAK_ENDED_AT_MS = "last_break_ended_at_ms"

    const val DEFAULT_BREAK_DURATION_MS = 5L * 60_000L
    const val DEFAULT_BREAK_COUNT = 3
    const val DEFAULT_MIN_BETWEEN_BREAKS_MS = 0L

    const val MAX_BREAK_DURATION_MS = 30L * 60_000L
    const val MAX_BREAK_COUNT = 10
    const val MAX_MIN_BETWEEN_BREAKS_MS = 60L * 60_000L

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

    fun lastBreakEndedAtMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BREAK_ENDED_AT_MS, 0L)

    fun setLastBreakEndedAtMs(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_LAST_BREAK_ENDED_AT_MS, value) }

    fun canTakeBreak(nowMs: Long, lastBreakEndedAtMs: Long, minBetweenBreaksMs: Long): Boolean {
        if (lastBreakEndedAtMs == 0L) return true
        return nowMs - lastBreakEndedAtMs >= minBetweenBreaksMs
    }

    fun nextBreakAvailableInMs(nowMs: Long, lastBreakEndedAtMs: Long, minBetweenBreaksMs: Long): Long {
        if (lastBreakEndedAtMs == 0L) return 0L
        val remaining = (lastBreakEndedAtMs + minBetweenBreaksMs) - nowMs
        return remaining.coerceAtLeast(0L)
    }
}
