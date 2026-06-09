package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app blocked by the Emergency tag, independent of the normal monitoring session.
 * Each tap of an Emergency tag adds (or refreshes) one row. The block is enforced until
 * [expiresAtMs] (24h from the tap) regardless of whether monitoring is on.
 */
@Entity(tableName = "emergency_blocks")
data class EmergencyBlock(
    @PrimaryKey val packageName: String,
    val expiresAtMs: Long,
)
