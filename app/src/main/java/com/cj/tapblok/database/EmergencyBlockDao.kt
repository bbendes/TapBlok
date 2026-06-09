package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyBlockDao {
    @Query("SELECT * FROM emergency_blocks")
    fun observeAll(): Flow<List<EmergencyBlock>>

    @Query("SELECT * FROM emergency_blocks")
    suspend fun getAllList(): List<EmergencyBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emergencyBlock: EmergencyBlock)

    @Query("DELETE FROM emergency_blocks WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpired(nowMs: Long)

    @Query("DELETE FROM emergency_blocks WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
