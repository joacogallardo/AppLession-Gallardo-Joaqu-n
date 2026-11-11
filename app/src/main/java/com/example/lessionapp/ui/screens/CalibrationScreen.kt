package com.example.lessionapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lessionapp.ui.components.ProaButton
import com.example.lessionapp.ui.components.ProaCard
import com.example.lessionapp.ui.components.ProaOutlinedButton
import com.example.lessionapp.ui.components.ProaSecondaryButton
import com.example.lessionapp.ui.components.ProaStatusBadge
import com.example.lessionapp.ui.components.ProaStatusState
import com.example.lessionapp.ui.theme.ProaBluePrimary
import com.example.lessionapp.ui.theme.ProaBlueSecondary
import com.example.lessionapp.ui.viewmodel.EmgViewModel

/**
 * Pantalla de Calibración del Sensor EMG V3 con establecimiento de límites mínimo (reposo) y máximo (MVC).
 */
@Composable
fun CalibrationScreen(viewModel: EmgViewModel) {
    val calibrationData by viewModel.calibrationData.collectAsState()
    val sensorData by viewModel.latestSensorData.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de Estado de Calibración
        ProaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Calibración",
                        tint = ProaBluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calibración del Sensor EMG V3",
                        style = MaterialTheme.typography.titleLarge,
                        color = ProaBluePrimary
                    )
                }
                ProaStatusBadge(
                    statusState = if (calibrationData.isCalibrated) ProaStatusState.NORMAL else ProaStatusState.WARNING,
                    customText = if (calibrationData.isCalibrated) "Calibrado" else "Por Calibrar"
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Establece los niveles de referencia mínimo (reposo absoluto) y máximo (contracción MVC) para interpretar correctamente la fuerza muscular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Nivel Actual de Activación Muscular (% MVC Calibrado)
        ProaCard {
            Text(
                text = "Activación Muscular en Tiempo Real (% MVC)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "%.1f %%".format(sensorData.activationPercentage),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = ProaBluePrimary
                )
                Text(
                    text = "Voltaje: %.1f mV".format(sensorData.filteredVoltageMv),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (sensorData.activationPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = ProaBluePrimary,
                trackColor = ProaBlueSecondary.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
        }

        // Límites de Calibración Guardados
        ProaCard {
            Text(
                text = "Valores de Referencia Guardados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Mínimo (Reposo V_min):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "%.1f mV".format(calibrationData.minVoltageMv),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProaBluePrimary
                    )
                }
                Column {
                    Text(
                        text = "Máximo (MVC V_max):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "%.1f mV".format(calibrationData.maxVoltageMv),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProaBlueSecondary
                    )
                }
            }
        }

        // Asistente de Calibración Guiado
        ProaCard {
            Text(
                text = "Asistente de Calibración Pasos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Paso 1: Muestrear Reposo
            Text(
                text = "Paso 1: Relaja el músculo por completo y presiona 'Calibrar Reposo'.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProaButton(
                text = "Calibrar Reposo (V_min)",
                onClick = { viewModel.calibrateRestLevel() },
                icon = Icons.Default.SelfImprovement,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Paso 2: Muestrear Contracción Máxima (MVC)
            Text(
                text = "Paso 2: Realiza tu máxima fuerza muscular y presiona 'Calibrar MVC'.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProaSecondaryButton(
                text = "Calibrar Contracción Máxima (V_max)",
                onClick = { viewModel.calibrateMaxLevel() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Restablecer
            ProaOutlinedButton(
                text = "Restablecer Calibración de Fábrica",
                onClick = { viewModel.resetCalibration() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
