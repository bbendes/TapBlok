package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<AppGroup>>

    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE")
    suspend fun getAllList(): List<AppGroup>

    @Query("SELECT * FROM app_groups WHERE id = :id")
    suspend fun getById(id: Long): AppGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AppGroup): Long

    @Update
    suspend fun update(group: AppGroup)

    @Delete
    suspend fun delete(group: AppGroup)
}
