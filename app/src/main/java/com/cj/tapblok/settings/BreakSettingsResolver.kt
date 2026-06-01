package com.cj.tapblok.settings

import com.cj.tapblok.database.AppGroup

data class GlobalBreakSettings(
    val durationMs: Long,
    val count: Int,
    val minBetweenMs: Long
)

data class EffectiveBreakSettings(
    val durationMs: Long,
    val count: Int,
    val minBetweenMs: Long
)

fun resolveBreakSettings(group: AppGroup?, global: GlobalBreakSettings): EffectiveBreakSettings =
    EffectiveBreakSettings(
        durationMs = group?.breakDurationMs ?: global.durationMs,
        count = group?.breakCount ?: global.count,
        minBetweenMs = group?.minBetweenBreaksMs ?: global.minBetweenMs
    )
