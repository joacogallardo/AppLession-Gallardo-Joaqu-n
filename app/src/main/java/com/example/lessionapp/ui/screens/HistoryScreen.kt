package com.example.lessionapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lessionapp.data.local.entity.EmgRecordEntity
import com.example.lessionapp.data.local.entity.EmgSessionEntity
import com.example.lessionapp.ui.components.ProaCard
import com.example.lessionapp.ui.components.ProaEmgChart
import com.example.lessionapp.ui.components.ProaOutlinedButton
import com.example.lessionapp.ui.components.ProaStatusBadge
import com.example.lessionapp.ui.components.ProaStatusState
import com.example.lessionapp.ui.theme.ProaBluePrimary
import com.example.lessionapp.ui.theme.ProaStatusAlert
import com.example.lessionapp.ui.viewmodel.EmgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: EmgViewModel,
    modifier: Modifier = Modifier,
    onDownloadSession: (Long) -> Unit
) {
    val sessions by viewModel.allSessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()
    val records by viewModel.selectedSessionRecords.collectAsState()
    var sessionPendingDelete by remember { mutableStateOf<EmgSessionEntity?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = ProaBluePrimary
                            )
                            Text(
                                text = "Entrenamientos guardados",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = ProaBluePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        ProaStatusBadge(
                            statusState = ProaStatusState.NORMAL,
                            customText = "${sessions.size}"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Los datos se almacenan localmente en la base Room de la aplicación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sessions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        ProaOutlinedButton(
                            text = "Eliminar todos los registros",
                            onClick = { confirmDeleteAll = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    ProaCard {
                        Text(
                            text = "No hay entrenamientos guardados.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "Seleccioná un entrenamiento para ver el gráfico y sus muestras.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProaBluePrimary
                    )
                }
                items(sessions, key = { it.sessionId }) { session ->
                    SessionItemCard(
                        session = session,
                        isSelected = session.sessionId == selectedSessionId,
                        onSelect = { viewModel.selectSession(session.sessionId) },
                        onDownload = { onDownloadSession(session.sessionId) },
                        onDelete = { sessionPendingDelete = session }
                    )
                }
                if (selectedSessionId != null) {
                    item {
                        ProaCard {
                            Text(
                                text = "Registro seleccionado · ${records.size} muestras",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ProaBluePrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "ADC y valor en mV guardados por muestra. En sesiones BLE nuevas, mV corresponde a la salida SIG (emgEnvelope) reportada por el firmware; en simulaciones es derivado del ADC.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (records.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                ProaEmgChart(
                                    dataPoints = records.map { it.voltageMv },
                                    maxValue = 3300.0
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "La curva representa los valores mV guardados. Las sesiones anteriores conservan sus datos originales.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Esta sesión no tiene muestras registradas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    items(records, key = { it.id }) { record ->
                        RecordRow(record = record)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Eliminar entrenamiento") },
            text = { Text("¿Eliminar “${session.sessionTitle}” y todas sus muestras? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session)
                        sessionPendingDelete = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Eliminar todo el historial") },
            text = { Text("Se eliminarán ${sessions.size} entrenamientos y todas sus muestras. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        confirmDeleteAll = false
                    }
                ) { Text("Eliminar todo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SessionItemCard(
    session: EmgSessionEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedDate = remember(session.startTime) {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(session.startTime))
    }

    ProaCard(
        modifier = Modifier.clickable(onClick = onSelect),
        borderColor = if (isSelected) ProaBluePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text("Fecha: $formattedDate", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    "Muestras: ${session.totalSamples} · Duración: ${session.durationSeconds}s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Valor mV guardado · promedio %.2f · mínimo %.2f · máximo %.2f".format(
                        session.avgVoltageMv,
                        session.minVoltageMv,
                        session.maxVoltageMv
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProaBluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Descargar datos del entrenamiento como CSV",
                        tint = ProaBluePrimary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar entrenamiento",
                        tint = ProaStatusAlert
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: EmgRecordEntity) {
    val formattedTime = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(record.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(formattedTime, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(
                "ADC: ${record.rawAdc} cuentas · valor mV guardado: %.2f".format(record.voltageMv),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (record.isAlert) ProaStatusAlert else ProaBluePrimary
            )
        }
    }
}
