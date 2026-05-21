package com.example.lifelayer.service

import android.os.Looper
import android.service.wallpaper.WallpaperService
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class MoriWallpaperServiceTest {

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testWallpaperServiceConstants() {
        assertEquals("MoriWallpaperPrefs", MoriWallpaperService.PREFS_NAME)
        assertEquals("grid_mode", MoriWallpaperService.KEY_MODE)
        assertEquals("yearly", MoriWallpaperService.MODE_YEARLY)
        assertEquals("weekly", MoriWallpaperService.MODE_WEEKLY)
        assertEquals("custom", MoriWallpaperService.MODE_CUSTOM)
        assertEquals("custom_total", MoriWallpaperService.KEY_CUSTOM_TOTAL)
        assertEquals("start_date_ms", MoriWallpaperService.KEY_START_DATE_MS)
        assertEquals("lock_screen_only", MoriWallpaperService.KEY_LOCK_ONLY)
        assertEquals("home_image_uri", MoriWallpaperService.KEY_HOME_IMAGE_URI)
        assertEquals("achievement_time", MoriWallpaperService.KEY_ACHIEVEMENT_TIME)
        assertEquals("aod_enabled", MoriWallpaperService.KEY_AOD_ENABLED)
    }

    @Test
    fun testEngineInstantiationMocked() {
        val service = spyk<MoriWallpaperService>()
        val mockEngine = mockk<WallpaperService.Engine>(relaxed = true)
        every { service.onCreateEngine() } returns mockEngine
        
        val engine = service.onCreateEngine()
        assertNotNull(engine)
    }
}
