package com.example.lifelayer.ui

import android.app.Application
import android.content.Intent
import app.cash.turbine.test
import com.example.lifelayer.data.AppDatabase
import com.example.lifelayer.data.MissionDao
import com.example.lifelayer.data.MissionEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val application: Application = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val dao: MissionDao = mockk(relaxed = true)
    
    private val activeMissionsFlow = MutableStateFlow<List<MissionEntity>>(emptyList())
    private val latestMissionFlow = MutableStateFlow<MissionEntity?>(null)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(AppDatabase.Companion)
        mockkStatic(android.util.Log::class)
        
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.missionDao() } returns dao
        
        every { dao.getActiveMissions() } returns activeMissionsFlow
        every { dao.getLatestMission() } returns latestMissionFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitializationLoadsMissions() = runTest {
        val sampleMissions = listOf(
            MissionEntity(id = 1, content = "M1", timestamp = 100L)
        )
        activeMissionsFlow.value = sampleMissions
        
        val viewModel = MainViewModel(application)
        
        // Let stateIn collect
        runCurrent()
        
        viewModel.activeMissions.test {
            // stateIn emits initialValue first
            assertEquals(emptyList<MissionEntity>(), awaitItem())
            assertEquals(sampleMissions, awaitItem())
        }
    }

    @Test
    fun testDeployMission() = runTest {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { invocation.self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { invocation.self as Intent }
        
        val viewModel = MainViewModel(application)
        
        val content = "Go to Mars"
        val targetDays = 30
        
        viewModel.deployMission(content, targetDays)
        
        // Wait for coroutine to run on viewmodel scope
        advanceUntilIdle()
        
        coVerify { dao.insert(any()) }
        
        val intentSlot = slot<Intent>()
        verify { application.startForegroundService(capture(intentSlot)) }
        
        verify { anyConstructed<Intent>().putExtra("MISSION_TITLE", content) }
        verify { anyConstructed<Intent>().putExtra("DURATION_MILLIS", targetDays.toLong() * 24 * 60 * 60 * 1000L) }
    }

    @Test
    fun testCompleteMission() = runTest {
        val viewModel = MainViewModel(application)
        val mission = MissionEntity(id = 1, content = "M1", timestamp = 100L, isCompleted = false)
        
        viewModel.completeMission(mission)
        
        advanceUntilIdle()
        
        coVerify { dao.update(match { it.id == 1 && it.isCompleted }) }
    }
}
