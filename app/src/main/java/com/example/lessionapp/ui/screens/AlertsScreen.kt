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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lessionapp.ui.components.ProaButton
import com.example.lessionapp.ui.components.ProaCard
import com.example.lessionapp.ui.components.ProaStatusBadge
import com.example.lessionapp.ui.components.ProaStatusState
import com.example.lessionapp.ui.theme.ProaBluePrimary
import com.example.lessionapp.ui.theme.ProaCyanAccent
import com.example.lessionapp.ui.viewmodel.EmgViewModel

/**
 * Pantalla de configuración del Sistema de Alertas (HU-02).
 */
@Composable
fun AlertsScreen(viewModel: EmgViewModel) {
    val alertConfig by viewModel.alertConfig.collectAsState()
    val isAlertActive by viewModel.isAlertActive.collectAsState()

    var minInput by remember(alertConfig) { mutableStateOf(alertConfig.minThresholdMv.toString()) }
    var maxInput by remember(alertConfig) { mutableStateOf(alertConfig.maxThresholdMv.toString()) }
    var enabledChecked by remember(alertConfig) { mutableStateOf(alertConfig.isAlertEnabled) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de Estado Actual de Alerta
        ProaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alertas",
                        tint = ProaBluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sistema de Alertas EMG",
                        style = MaterialTheme.typography.titleLarge,
                        color = ProaBluePrimary
                    )
                }
                ProaStatusBadge(
                    statusState = if (isAlertActive) ProaStatusState.ALERT else ProaStatusState.NORMAL,
                    customText = if (isAlertActive) "¡ALERTA ACTIVA!" else "Normal"
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Define los límites mínimo y máximo de tensión muscular en mV para activar notificaciones visuales automáticas cuando se superen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Configuración de Umbrales (HU-02 Tarea 1)
        ProaCard {
            Text(
                text = "Configuración de Umbrales (mV)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Activar / Desactivar Sistema
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Habilitar Alertas Visuales",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = enabledChecked,
                    onCheckedChange = { enabledChecked = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ProaBluePrimary,
                        checkedTrackColor = ProaCyanAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Umbral Mínimo
            OutlinedTextField(
                value = minInput,
                onValueChange = { minInput = it },
                label = { Text("Umbral Mínimo (mV)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Umbral Máximo
            OutlinedTextField(
                value = maxInput,
                onValueChange = { maxInput = it },
                label = { Text("Umbral Máximo (mV)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            ProaButton(
                text = "Guardar Configuración de Alertas",
                onClick = {
                    val minVal = minInput.toDoubleOrNull() ?: 50.0
                    val maxVal = maxInput.toDoubleOrNull() ?: 2500.0
                    viewModel.updateAlertConfig(minVal, maxVal, enabledChecked)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
