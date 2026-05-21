package com.example.lifelayer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.lifelayer.MainActivity
import java.util.Locale

class MissionService : Service() {
    private val CHANNEL_ID = "COMMAND_STRIP"
    private val NOTIFICATION_ID = 1
    private var missionTitle: String = ""
    private var startTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        missionTitle = intent?.getStringExtra("MISSION_TITLE") ?: "NO OBJECTIVE SET"
        startTime = intent?.getLongExtra("START_TIME", System.currentTimeMillis()) ?: System.currentTimeMillis()

        createNotificationChannel()
        
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        handler.post(updateRunnable)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val elapsed = System.currentTimeMillis() - startTime
        val duration = 12 * 60 * 60 * 1000L
        val remaining = (duration - elapsed).coerceAtLeast(0)
        
        val hours = (remaining / (1000 * 60 * 60))
        val minutes = (remaining / (1000 * 60)) % 60
        val seconds = (remaining / 1000) % 60
        
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        
        val completeIntent = Intent(this, MissionReceiver::class.java).apply {
            action = "ACTION_COMPLETE_MISSION"
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            this, 0, completeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val color = if (elapsed > 11 * 60 * 60 * 1000L) {
            0x8B0000 // BloodRed
        } else {
            0xD4AF37 // CommandGold
        }

        val subtext = when {
            elapsed > 11 * 60 * 60 * 1000L -> "FINAL PUSH. NO EXCUSES."
            elapsed > 6 * 60 * 60 * 1000L -> "50% EXPIRED. STATUS REQUIRED."
            else -> "NO RETREAT, NO EXCUSES."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("MISSION: $missionTitle")
            .setContentText("TIME REMAINING: $timeStr | $subtext")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(color)
            .setColorized(true)
            .setOnlyAlertOnce(true) // This prevents the sound/vibration on every update
            .setSilent(true) // Ensure the notification is silent during updates
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "COMPLETE", completePendingIntent)
            .setProgress(12 * 60 * 60, (elapsed / 1000).toInt(), false)
            .setStyle(NotificationCompat.BigTextStyle().bigText("OBJECTIVE: $missionTitle\n$subtext\nRemaining: $timeStr"))
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
        
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 11 * 60 * 60 * 1000L && (elapsed / 1000) % 1800 == 0L) {
            triggerHeartbeat()
        }
    }

    private fun triggerHeartbeat() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Commander Service", 
                NotificationManager.IMPORTANCE_LOW // Set to LOW to prevent sound by default
            ).apply {
                description = "Strict mission accountability strip"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = 0xD4AF37
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
