package com.cj.tapblok.settings

import com.cj.tapblok.database.AppGroup
import com.cj.tapblok.database.GroupTimeRule
import java.util.Calendar

data class GlobalBreakSettings(
    val durationMs: Long,
    val count: Int,
    val minBetweenMs: Long,
    val minDelayBeforeFirstBreakMs: Long = 0L,
)

data class EffectiveBreakSettings(
    val durationMs: Long,
    val count: Int,
    val minBetweenMs: Long,
    val minDelayBeforeFirstBreakMs: Long = 0L,
)

data class ResolvedGroupSettings(
    val durationMs: Long,
    val count: Int,
    val minBetweenMs: Long,
    val minDelayBeforeFirstBreakMs: Long,
    val blockingEnabled: Boolean,
    val matchedRuleId: Long?,
)

fun resolveBreakSettings(group: AppGroup?, global: GlobalBreakSettings): EffectiveBreakSettings =
    EffectiveBreakSettings(
        durationMs = group?.breakDurationMs ?: global.durationMs,
        count = group?.breakCount ?: global.count,
        minBetweenMs = group?.minBetweenBreaksMs ?: global.minBetweenMs,
        minDelayBeforeFirstBreakMs = group?.minDelayBeforeFirstBreakMs ?: global.minDelayBeforeFirstBreakMs,
    )

/**
 * Resolve effective break settings for a group at a given wall-clock moment.
 *
 * Order of precedence per field: matched time rule override -> group static override -> global.
 * Rules are filtered to those whose day-of-week bit matches and whose [startMinuteOfDay,
 * endMinuteOfDay) window contains [nowMinuteOfDay]; a window where end <= start spans midnight.
 * If multiple rules match, the lowest (priority, id) wins. With no matching rule,
 * [ResolvedGroupSettings.blockingEnabled] is `true`.
 */
fun resolveForGroup(
    group: AppGroup?,
    rules: List<GroupTimeRule>,
    nowDayOfWeekBit: Int,
    nowMinuteOfDay: Int,
    global: GlobalBreakSettings,
): ResolvedGroupSettings {
    val matched = rules
        .asSequence()
        .filter { ruleMatches(it, nowDayOfWeekBit, nowMinuteOfDay) }
        .sortedWith(compareBy({ it.priority }, { it.id }))
        .firstOrNull()

    val duration = matched?.breakDurationMsOverride ?: group?.breakDurationMs ?: global.durationMs
    val count = matched?.breakCountOverride ?: group?.breakCount ?: global.count
    val minBetween = matched?.minBetweenBreaksMsOverride ?: group?.minBetweenBreaksMs ?: global.minBetweenMs
    val minDelay = matched?.minDelayBeforeFirstBreakMsOverride
        ?: group?.minDelayBeforeFirstBreakMs
        ?: global.minDelayBeforeFirstBreakMs
    val blockingEnabled = matched?.blockingEnabled ?: true

    return ResolvedGroupSettings(
        durationMs = duration,
        count = count,
        minBetweenMs = minBetween,
        minDelayBeforeFirstBreakMs = minDelay,
        blockingEnabled = blockingEnabled,
        matchedRuleId = matched?.id
    )
}

private fun ruleMatches(rule: GroupTimeRule, dayBit: Int, minuteOfDay: Int): Boolean {
    if ((rule.daysOfWeekMask and (1 shl dayBit)) == 0) return false
    return if (rule.endMinuteOfDay > rule.startMinuteOfDay) {
        minuteOfDay >= rule.startMinuteOfDay && minuteOfDay < rule.endMinuteOfDay
    } else {
        // Span midnight (or zero-length window): match if at-or-after start OR before end.
        minuteOfDay >= rule.startMinuteOfDay || minuteOfDay < rule.endMinuteOfDay
    }
}

/**
 * Day-of-week bit index for the current moment, matching [GroupTimeRule.daysOfWeekMask]
 * (bit0=Mon ... bit6=Sun).
 */
fun currentDayOfWeekBit(nowMs: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    // Calendar.DAY_OF_WEEK: Sunday=1 ... Saturday=7. Map to Mon=0..Sun=6.
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}

fun currentMinuteOfDay(nowMs: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}
