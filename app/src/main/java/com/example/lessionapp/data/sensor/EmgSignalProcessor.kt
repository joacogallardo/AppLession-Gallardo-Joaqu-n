package com.example.lessionapp.data.sensor

import kotlin.math.sqrt

/**
 * Modelo de datos procesados del sensor EMG V3 incluyendo la activación muscular calibrada (% MVC).
 */
data class EmgSensorData(
    val rawAdc: Int,               // Valor digital directo del ADC de 12 bits (0 - 4095)
    val voltageMv: Double,         // mV medidos por firmware en SIG para BLE o derivados del ADC en simulación
    val filteredVoltageMv: Double, // Media móvil calculada de la señal de salida
    val rmsVoltageMv: Double,      // RMS calculado de la señal de salida; no es lectura directa del sensor
    val activationPercentage: Double = 0.0, // Porcentaje de activación muscular calibrado (% MVC 0-100%)
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Procesador de señal para el sensor EMG V3 de superficie.
 */
class EmgSignalProcessor(
    private val vRefMv: Double = 3300.0,   // 3.3V = 3300 mV
    private val adcResolution: Int = 4095,  // Resolución 12-bit
    private val filterWindowSize: Int = 10
) {
    private val buffer = DoubleArray(filterWindowSize)
    private var bufferIndex = 0
    private var isBufferFull = false

    fun adcToMillivolts(rawAdc: Int): Double {
        val clampedAdc = rawAdc.coerceIn(0, adcResolution)
        return (clampedAdc.toDouble() / adcResolution) * vRefMv
    }

    /**
     * Procesamiento completo de la señal incluyendo la normalización con límites de calibración.
     */
    fun processSample(
        rawAdc: Int,
        minVoltageMv: Double = 100.0,
        maxVoltageMv: Double = 3000.0,
        measuredOutputMv: Double? = null
    ): EmgSensorData {
        val rawVoltageMv = measuredOutputMv ?: adcToMillivolts(rawAdc)

        buffer[bufferIndex] = rawVoltageMv
        bufferIndex = (bufferIndex + 1) % filterWindowSize
        if (bufferIndex == 0) isBufferFull = true

        val currentSize = if (isBufferFull) filterWindowSize else bufferIndex.coerceAtLeast(1)

        var sum = 0.0
        var squareSum = 0.0
        for (i in 0 until currentSize) {
            val v = buffer[i]
            sum += v
            squareSum += v * v
        }

        val movingAverage = sum / currentSize
        val rms = sqrt(squareSum / currentSize)

        // Cálculo de % de Activación Muscular (% MVC)
        val range = maxVoltageMv - minVoltageMv
        val percentage = if (range > 0.0) {
            ((movingAverage - minVoltageMv) / range) * 100.0
        } else {
            0.0
        }
        val clampedPercentage = percentage.coerceIn(0.0, 100.0)

        return EmgSensorData(
            rawAdc = rawAdc,
            voltageMv = rawVoltageMv,
            filteredVoltageMv = movingAverage,
            rmsVoltageMv = rms,
            activationPercentage = clampedPercentage
        )
    }

    fun resetFilter() {
        buffer.fill(0.0)
        bufferIndex = 0
        isBufferFull = false
    }
}
