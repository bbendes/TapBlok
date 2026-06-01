package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_groups")
data class AppGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val breakDurationMs: Long? = null,
    val breakCount: Int? = null,
    val minBetweenBreaksMs: Long? = null
)
