package com.example.lifelayer.data

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MissionRepositoryTest {

    private val missionDao: MissionDao = mockk(relaxed = true)

    @Test
    fun testGetLatestMission() = runTest {
        val sampleMission = MissionEntity(id = 1, content = "Test Mission", timestamp = 12345L)
        every { missionDao.getLatestMission() } returns flowOf(sampleMission)
        val repository = MissionRepository(missionDao)

        repository.latestMission.test {
            assertEquals(sampleMission, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun testGetActiveMissions() = runTest {
        val sampleMissions = listOf(
            MissionEntity(id = 1, content = "Mission 1", timestamp = 12345L),
            MissionEntity(id = 2, content = "Mission 2", timestamp = 12346L)
        )
        every { missionDao.getActiveMissions() } returns flowOf(sampleMissions)
        val repository = MissionRepository(missionDao)

        repository.activeMissions.test {
            assertEquals(sampleMissions, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun testDeployMission() = runTest {
        val content = "New Objective"
        val targetDays = 10
        val slot = slot<MissionEntity>()
        coEvery { missionDao.insert(capture(slot)) } returns Unit
        val repository = MissionRepository(missionDao)

        repository.deployMission(content, targetDays)

        coVerify(exactly = 1) { missionDao.insert(any()) }
        assertEquals(content, slot.captured.content)
        assertEquals(targetDays, slot.captured.targetDays)
        assertEquals(targetDays.toLong() * 24 * 60 * 60 * 1000L, slot.captured.durationMillis)
    }

    @Test
    fun testCompleteMission() = runTest {
        val mission = MissionEntity(id = 1, content = "Test Mission", timestamp = 12345L, isCompleted = false)
        val slot = slot<MissionEntity>()
        coEvery { missionDao.update(capture(slot)) } returns Unit
        val repository = MissionRepository(missionDao)

        repository.completeMission(mission)

        coVerify(exactly = 1) { missionDao.update(any()) }
        assertTrue(slot.captured.isCompleted)
        assertEquals(mission.id, slot.captured.id)
    }

    @Test
    fun testFailMission() = runTest {
        val mission = MissionEntity(id = 1, content = "Test Mission", timestamp = 12345L, isFailed = false)
        val slot = slot<MissionEntity>()
        coEvery { missionDao.update(capture(slot)) } returns Unit
        val repository = MissionRepository(missionDao)

        repository.failMission(mission)

        coVerify(exactly = 1) { missionDao.update(any()) }
        assertTrue(slot.captured.isFailed)
        assertEquals(mission.id, slot.captured.id)
    }

    @Test
    fun testGetActiveMission() = runTest {
        val sampleMission = MissionEntity(id = 1, content = "Active Mission", timestamp = 12345L)
        coEvery { missionDao.getActiveMissionSync(any()) } returns sampleMission
        val repository = MissionRepository(missionDao)

        val result = repository.getActiveMission()

        assertEquals(sampleMission, result)
    }
}
