package com.example.lifelayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lifelayer.data.AppDatabase
import com.example.lifelayer.data.MissionRepository
import com.example.lifelayer.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MissionReceiver", "Received action: ${intent.action}")
        if (intent.action == "ACTION_COMPLETE_MISSION") {
            val database = AppDatabase.getDatabase(context)
            val repository = MissionRepository(database.missionDao())
            
            CoroutineScope(AppDispatchers.IO).launch {
                val activeMission = repository.getActiveMission()
                activeMission?.let {
                    repository.completeMission(it)
                    
                    // Signal the wallpaper to show achievement mode
                    val prefs = context.getSharedPreferences(MoriWallpaperService.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putLong(MoriWallpaperService.KEY_ACHIEVEMENT_TIME, System.currentTimeMillis()).apply()
                }
                // Stop the service
                context.stopService(Intent(context, MissionService::class.java))
            }
        }
    }
}
