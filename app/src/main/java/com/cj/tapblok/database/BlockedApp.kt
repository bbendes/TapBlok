package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_apps",
    foreignKeys = [
        ForeignKey(
            entity = AppGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
data class BlockedApp(
    @PrimaryKey
    val packageName: String,
    val groupId: Long? = null
)
