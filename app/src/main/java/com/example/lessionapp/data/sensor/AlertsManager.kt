package com.example.lessionapp.data.sensor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Configuración de umbrales para el sistema de alertas (HU-02).
 */
data class EmgAlertConfig(
    val minThresholdMv: Double = 50.0,    // Umbral mínimo de tensión EMG (mV)
    val maxThresholdMv: Double = 2500.0,  // Umbral máximo de tensión EMG (mV)
    val isAlertEnabled: Boolean = true    // Sistema de alertas activo
)

/**
 * Gestor de la configuración de alertas con persistencia local.
 */
class AlertsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "emg_alerts_preferences"
        private const val KEY_MIN_THRESHOLD = "key_min_threshold_mv"
        private const val KEY_MAX_THRESHOLD = "key_max_threshold_mv"
        private const val KEY_IS_ENABLED = "key_is_alert_enabled"

        const val DEFAULT_MIN_MV = 50.0
        const val DEFAULT_MAX_MV = 2500.0
    }

    /**
     * Carga la configuración de alertas almacenada.
     */
    fun loadConfig(): EmgAlertConfig {
        val minMv = prefs.getFloat(KEY_MIN_THRESHOLD, DEFAULT_MIN_MV.toFloat()).toDouble()
        val maxMv = prefs.getFloat(KEY_MAX_THRESHOLD, DEFAULT_MAX_MV.toFloat()).toDouble()
        val enabled = prefs.getBoolean(KEY_IS_ENABLED, true)

        return EmgAlertConfig(
            minThresholdMv = minMv,
            maxThresholdMv = maxMv,
            isAlertEnabled = enabled
        )
    }

    /**
     * Guarda la configuración de umbrales y estado de alertas.
     */
    fun saveConfig(config: EmgAlertConfig) {
        prefs.edit {
            putFloat(KEY_MIN_THRESHOLD, config.minThresholdMv.toFloat())
            putFloat(KEY_MAX_THRESHOLD, config.maxThresholdMv.toFloat())
            putBoolean(KEY_IS_ENABLED, config.isAlertEnabled)
        }
    }
}
