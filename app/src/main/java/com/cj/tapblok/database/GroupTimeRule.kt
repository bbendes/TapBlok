package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_time_rules",
    foreignKeys = [
        ForeignKey(
            entity = AppGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class GroupTimeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    // bit0=Mon, bit1=Tue, ... bit6=Sun
    val daysOfWeekMask: Int,
    // 0..1439
    val startMinuteOfDay: Int,
    // 0..1439; if endMinuteOfDay <= startMinuteOfDay the window spans midnight
    val endMinuteOfDay: Int,
    // lower priority wins; tiebreak by id ascending
    val priority: Int = 0,
    val blockingEnabled: Boolean = true,
    val breakCountOverride: Int? = null,
    val breakDurationMsOverride: Long? = null,
    val minBetweenBreaksMsOverride: Long? = null,
)
