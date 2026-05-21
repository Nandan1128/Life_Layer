package com.example.lifelayer.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.lifelayer.data.AppDatabase
import com.example.lifelayer.data.MissionDao
import com.example.lifelayer.data.MissionEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val dao: MissionDao = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefsEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkObject(AppDatabase.Companion)
        mockkStatic(android.util.Log::class)

        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.missionDao() } returns dao
        
        com.example.lifelayer.util.AppDispatchers.IO = testDispatcher

        every { context.getSharedPreferences(MoriWallpaperService.PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.edit() } returns sharedPrefsEditor
        every { sharedPrefsEditor.putLong(any(), any()) } returns sharedPrefsEditor
    }

    @After
    fun tearDown() {
        com.example.lifelayer.util.AppDispatchers.IO = Dispatchers.IO
        unmockkAll()
    }

    @Test
    fun testOnReceiveCompleteMission() = runTest {
        com.example.lifelayer.util.AppDispatchers.IO = StandardTestDispatcher(testScheduler)

        // Prepare active mission
        val activeMission = MissionEntity(id = 42, content = "Active", timestamp = 1000L, isCompleted = false)
        coEvery { dao.getActiveMissionSync(any()) } returns activeMission
        coEvery { dao.update(any()) } returns Unit

        every { intent.action } returns "ACTION_COMPLETE_MISSION"

        val receiver = MissionReceiver()
        receiver.onReceive(context, intent)

        // Advance coroutines on Dispatchers.IO (mocked as testDispatcher)
        advanceUntilIdle()

        // Verify database updates
        coVerify(exactly = 1) { dao.update(match { it.id == 42 && it.isCompleted }) }

        // Verify wallpaper prefs updated
        verify(exactly = 1) { sharedPrefsEditor.putLong(MoriWallpaperService.KEY_ACHIEVEMENT_TIME, any()) }
        verify(exactly = 1) { sharedPrefsEditor.apply() }

        // Verify service stopped
        verify(exactly = 1) { context.stopService(any()) }
    }

    @Test
    fun testOnReceiveOtherActionDoesNothing() = runTest {
        com.example.lifelayer.util.AppDispatchers.IO = StandardTestDispatcher(testScheduler)

        every { intent.action } returns "SOME_OTHER_ACTION"

        val receiver = MissionReceiver()
        receiver.onReceive(context, intent)

        advanceUntilIdle()

        coVerify(exactly = 0) { dao.getActiveMissionSync(any()) }
        verify(exactly = 0) { context.stopService(any()) }
    }
}
