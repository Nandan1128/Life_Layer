package com.example.lifelayer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MissionServiceTest {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val vibrator: Vibrator = mockk(relaxed = true)
    private val mockNotification = mockk<Notification>(relaxed = true)

    private lateinit var service: MissionService

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        mockkStatic(PendingIntent::class)
        mockkStatic(android.util.Log::class)
        mockkConstructor(NotificationCompat.Builder::class)

        every { android.util.Log.d(any(), any()) } returns 0
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk(relaxed = true)
        
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockNotification

        // Instantiate and spy on the final service class directly
        service = spyk(MissionService())

        // Mock final methods of Service and ContextWrapper
        every { service.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { service.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        every { service.getSystemService(NotificationManager::class.java) } returns notificationManager
        every { service.packageName } returns "com.example.lifelayer"
        every { service.applicationContext } returns service

        // Mock final startForeground methods
        every { service.startForeground(any(), any()) } returns Unit
        every { service.startForeground(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testOnStartCommandStartsForegroundAndSetsTitle() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra("MISSION_TITLE") } returns "Test Service Mission"
        every { intent.getLongExtra("START_TIME", any()) } returns 1000L

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_STICKY, result)
        
        // Verify startForeground was called
        verify(exactly = 1) { 
            service.startForeground(1, mockNotification)
        }
    }

    @Test
    fun testOnBindReturnsNull() {
        val binder = service.onBind(null)
        assertNull(binder)
    }
}
