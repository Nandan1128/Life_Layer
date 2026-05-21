package com.example.lifelayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long,
    val durationMillis: Long = 12 * 60 * 60 * 1000L,
    val targetDays: Int = 365,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false
)
