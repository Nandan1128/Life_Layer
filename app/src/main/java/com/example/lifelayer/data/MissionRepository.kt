package com.example.lifelayer.data

import kotlinx.coroutines.flow.Flow

class MissionRepository(private val missionDao: MissionDao) {
    val latestMission: Flow<MissionEntity?> = missionDao.getLatestMission()
    val activeMissions: Flow<List<MissionEntity>> = missionDao.getActiveMissions()

    suspend fun deployMission(content: String, targetDays: Int = 365) {
        val mission = MissionEntity(
            content = content,
            timestamp = System.currentTimeMillis(),
            targetDays = targetDays,
            durationMillis = targetDays.toLong() * 24 * 60 * 60 * 1000L
        )
        missionDao.insert(mission)
    }

    suspend fun completeMission(mission: MissionEntity) {
        missionDao.update(mission.copy(isCompleted = true))
    }

    suspend fun failMission(mission: MissionEntity) {
        missionDao.update(mission.copy(isFailed = true))
    }

    suspend fun getActiveMission(): MissionEntity? {
        return missionDao.getActiveMissionSync(System.currentTimeMillis())
    }
}
