package com.example.lifelayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY timestamp DESC")
    fun getAllMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE isCompleted = 0 AND isFailed = 0 ORDER BY timestamp DESC")
    fun getActiveMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMission(): Flow<MissionEntity?>

    @Insert
    suspend fun insert(mission: MissionEntity)

    @Update
    suspend fun update(mission: MissionEntity)

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun getMissionById(id: Int): MissionEntity?

    @Query("SELECT * FROM missions WHERE isCompleted = 0 AND isFailed = 0 AND (timestamp + durationMillis) > :currentTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveMissionSync(currentTime: Long): MissionEntity?
}
