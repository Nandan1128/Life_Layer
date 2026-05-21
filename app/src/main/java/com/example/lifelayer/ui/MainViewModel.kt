package com.example.lifelayer.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifelayer.data.AppDatabase
import com.example.lifelayer.data.MissionEntity
import com.example.lifelayer.data.MissionRepository
import com.example.lifelayer.service.MissionService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MissionRepository

    val activeMissions: StateFlow<List<MissionEntity>>
    val latestMission: StateFlow<MissionEntity?>

    init {
        val dao = AppDatabase.getDatabase(application).missionDao()
        repository = MissionRepository(dao)
        
        activeMissions = repository.activeMissions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        latestMission = repository.latestMission.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    fun deployMission(content: String, targetDays: Int) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Deploying mission: $content for $targetDays days")
                repository.deployMission(content, targetDays)

                // Optional: Start notification service for the latest mission
                val durationMillis = targetDays.toLong() * 24 * 60 * 60 * 1000L
                val intent = Intent(getApplication(), MissionService::class.java).apply {
                    putExtra("MISSION_TITLE", content)
                    putExtra("START_TIME", System.currentTimeMillis())
                    putExtra("DURATION_MILLIS", durationMillis)
                }
                getApplication<Application>().startForegroundService(intent)
                Log.d("MainViewModel", "Service start command sent")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to deploy mission", e)
            }
        }
    }

    fun completeMission(mission: MissionEntity) {
        viewModelScope.launch {
            repository.completeMission(mission)
        }
    }
}
