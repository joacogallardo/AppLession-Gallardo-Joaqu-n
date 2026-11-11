package com.example.lessionapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa una sesión completa de registro de actividad del sensor EMG V3.
 */
@Entity(tableName = "emg_sessions")
data class EmgSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val sessionTitle: String = "Sesión EMG",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val avgVoltageMv: Double = 0.0,
    val maxVoltageMv: Double = 0.0,
    val minVoltageMv: Double = 0.0,
    val totalSamples: Int = 0
)
