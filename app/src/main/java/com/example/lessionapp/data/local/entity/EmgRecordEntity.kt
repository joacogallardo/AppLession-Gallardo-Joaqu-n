package com.example.lessionapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa cada lectura individual del sensor EMG V3 almacenada en la base de datos interna.
 */
@Entity(tableName = "emg_records")
data class EmgRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val voltageMv: Double,
    val rawAdc: Int,
    val isAlert: Boolean = false
)
