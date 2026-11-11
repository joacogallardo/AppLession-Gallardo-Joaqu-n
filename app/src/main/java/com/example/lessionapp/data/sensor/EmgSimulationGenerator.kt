package com.example.lessionapp.data.sensor

import kotlin.math.sin
import kotlin.random.Random

/**
 * Generador de datos simulados fisiológicamente realistas para el sensor EMG V3.
 * Permite probar la aplicación sin requerir la conexión física con el ESP32.
 */
class EmgSimulationGenerator {

    private var timeStep = 0.0
    private var isContractionPhase = false
    private var contractionCounter = 0

    fun reset() {
        timeStep = 0.0
        isContractionPhase = false
        contractionCounter = 0
    }

    /**
     * Genera un valor digital de ADC de 12 bits (0 - 4095) simulando patrones de actividad muscular.
     * Alterna entre fases de reposo muscular (ruido de fondo ~150-300 ADC) y fases de contracción muscular
     * con impulsos sinusoidales y picos característicos de la señal EMG.
     */
    fun generateNextAdcSample(): Int {
        timeStep += 0.1
        contractionCounter++

        // Alternar automáticamente fases de contracción y reposo cada cierto número de muestras
        if (contractionCounter % 30 == 0) {
            isContractionPhase = !isContractionPhase
        }

        val baseAdc = if (isContractionPhase) {
            // Fase de Contracción Muscular Activa
            val wave = (sin(timeStep) * 1200.0) + 2000.0
            val muscleNoise = Random.nextDouble(-300.0, 300.0)
            wave + muscleNoise
        } else {
            // Fase de Reposo Muscular
            val restBase = 250.0
            val restNoise = Random.nextDouble(-50.0, 50.0)
            restBase + restNoise
        }

        return baseAdc.toInt().coerceIn(0, 4095)
    }

    /**
     * Fuerza la generación de un pico de máxima contracción voluntaria (MVC) para pruebas.
     */
    fun generateMaxContractionSample(): Int {
        val peak = 3600 + Random.nextInt(-100, 200)
        return peak.coerceIn(0, 4095)
    }

    /**
     * Fuerza la generación de una muestra de reposo absoluto.
     */
    fun generateRestSample(): Int {
        val rest = 180 + Random.nextInt(-20, 20)
        return rest.coerceIn(0, 4095)
    }
}
