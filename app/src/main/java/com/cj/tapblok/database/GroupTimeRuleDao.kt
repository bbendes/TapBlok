package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupTimeRuleDao {
    @Query("SELECT * FROM group_time_rules WHERE groupId = :groupId ORDER BY priority ASC, id ASC")
    suspend fun getRulesForGroup(groupId: Long): List<GroupTimeRule>

    @Query("SELECT * FROM group_time_rules WHERE groupId = :groupId ORDER BY priority ASC, id ASC")
    fun observeRulesForGroup(groupId: Long): Flow<List<GroupTimeRule>>

    @Query("SELECT * FROM group_time_rules ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<GroupTimeRule>>

    @Query("SELECT COUNT(*) FROM group_time_rules WHERE groupId = :groupId")
    fun observeRuleCount(groupId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: GroupTimeRule): Long

    @Update
    suspend fun update(rule: GroupTimeRule)

    @Delete
    suspend fun delete(rule: GroupTimeRule)
}
