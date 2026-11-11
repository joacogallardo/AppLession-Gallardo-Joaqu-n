package com.example.lessionapp.data.sensor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Datos de calibración con los valores de referencia mínimo (reposo) y máximo (contracción MVC).
 */
data class EmgCalibrationData(
    val minVoltageMv: Double = 100.0,   // Nivel de tensión muscular en reposo absoluto (mV)
    val maxVoltageMv: Double = 3000.0,  // Nivel de tensión en máxima contracción voluntaria (mV)
    val isCalibrated: Boolean = false
)

/**
 * Gestor de la calibración del sensor EMG V3 con persistencia de preferencias locales.
 */
class CalibrationManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "emg_calibration_preferences"
        private const val KEY_MIN_VOLTAGE = "key_min_voltage_mv"
        private const val KEY_MAX_VOLTAGE = "key_max_voltage_mv"
        private const val KEY_IS_CALIBRATED = "key_is_calibrated"

        const val DEFAULT_MIN_VOLTAGE_MV = 100.0
        const val DEFAULT_MAX_VOLTAGE_MV = 3000.0
    }

    /**
     * Carga los valores de calibración almacenados.
     */
    fun loadCalibration(): EmgCalibrationData {
        val minMv = prefs.getFloat(KEY_MIN_VOLTAGE, DEFAULT_MIN_VOLTAGE_MV.toFloat()).toDouble()
        val maxMv = prefs.getFloat(KEY_MAX_VOLTAGE, DEFAULT_MAX_VOLTAGE_MV.toFloat()).toDouble()
        val isCalibrated = prefs.getBoolean(KEY_IS_CALIBRATED, false)

        return EmgCalibrationData(
            minVoltageMv = minMv,
            maxVoltageMv = maxMv,
            isCalibrated = isCalibrated
        )
    }

    /**
     * Guarda el nivel de reposo (mínimo).
     */
    fun saveMinVoltage(minMv: Double) {
        val validMin = minMv.coerceAtLeast(0.0)
        prefs.edit {
            putFloat(KEY_MIN_VOLTAGE, validMin.toFloat())
            putBoolean(KEY_IS_CALIBRATED, true)
        }
    }

    /**
     * Guarda el nivel de contracción máxima (máximo).
     */
    fun saveMaxVoltage(maxMv: Double) {
        val current = loadCalibration()
        val validMax = maxMv.coerceAtLeast(current.minVoltageMv + 100.0)
        prefs.edit {
            putFloat(KEY_MAX_VOLTAGE, validMax.toFloat())
            putBoolean(KEY_IS_CALIBRATED, true)
        }
    }

    /**
     * Guarda ambos límites de calibración simultáneamente.
     */
    fun saveCalibration(minMv: Double, maxMv: Double) {
        val validMin = minMv.coerceAtLeast(0.0)
        val validMax = maxMv.coerceAtLeast(validMin + 100.0)
        prefs.edit {
            putFloat(KEY_MIN_VOLTAGE, validMin.toFloat())
            putFloat(KEY_MAX_VOLTAGE, validMax.toFloat())
            putBoolean(KEY_IS_CALIBRATED, true)
        }
    }

    /**
     * Restablece la calibración a los valores de fábrica.
     */
    fun resetCalibration() {
        prefs.edit {
            putFloat(KEY_MIN_VOLTAGE, DEFAULT_MIN_VOLTAGE_MV.toFloat())
            putFloat(KEY_MAX_VOLTAGE, DEFAULT_MAX_VOLTAGE_MV.toFloat())
            putBoolean(KEY_IS_CALIBRATED, false)
        }
    }

    /**
     * Calcula el porcentaje de activación muscular (% MVC) entre el mínimo y máximo calibrado.
     */
    fun calculateActivationPercentage(currentMv: Double, minMv: Double, maxMv: Double): Double {
        val range = maxMv - minMv
        if (range <= 0.0) return 0.0
        val percentage = ((currentMv - minMv) / range) * 100.0
        return percentage.coerceIn(0.0, 100.0)
    }
}
