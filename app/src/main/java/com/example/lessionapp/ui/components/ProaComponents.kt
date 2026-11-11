package com.example.lessionapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lessionapp.ui.theme.ProaBluePrimary
import com.example.lessionapp.ui.theme.ProaBlueSecondary
import com.example.lessionapp.ui.theme.ProaStatusAlert
import com.example.lessionapp.ui.theme.ProaStatusNormal
import com.example.lessionapp.ui.theme.ProaStatusWarning

/**
 * Estados posibles para las insignias de estado (ProaStatusBadge)
 */
enum class ProaStatusState(
    val label: String,
    val color: Color
) {
    NORMAL("Normal", ProaStatusNormal),
    WARNING("Advertencia", ProaStatusWarning),
    ALERT("Alerta Crítica", ProaStatusAlert),
    DISCONNECTED("Desconectado", Color.Gray)
}

/**
 * Barra de Navegación Superior Unificada de PROA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProaTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        },
        navigationIcon = navigationIcon ?: {},
        actions = actions ?: {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ProaBluePrimary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

/**
 * Tarjeta de Contenido con Identidad PROA
 */
@Composable
fun ProaCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

/**
 * Botón Principal con Estilo PROA
 */
@Composable
fun ProaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ProaBluePrimary,
            contentColor = Color.White,
            disabledContainerColor = Color.LightGray
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            softWrap = true
        )
    }
}

/**
 * Botón Secundario con Estilo PROA
 */
@Composable
fun ProaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ProaBlueSecondary,
            contentColor = Color.White
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            softWrap = true
        )
    }
}

/**
 * Botón Delineado con Estilo PROA
 */
@Composable
fun ProaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, ProaBluePrimary)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = ProaBluePrimary,
            softWrap = true
        )
    }
}

/**
 * Insignia / Chip de Estado Visual
 */
@Composable
fun ProaStatusBadge(
    statusState: ProaStatusState,
    modifier: Modifier = Modifier,
    customText: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = statusState.color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, statusState.color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusState.color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = customText ?: statusState.label,
                style = MaterialTheme.typography.labelLarge,
                color = statusState.color,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Tarjeta de Métrica Reutilizable (Voltaje EMG, Tiempo de Actividad, etc.)
 */
@Composable
fun ProaMetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
    statusState: ProaStatusState = ProaStatusState.NORMAL
) {
    ProaCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        ProaStatusBadge(statusState = statusState)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            if (icon != Icons.Default.Info) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProaBluePrimary,
                    modifier = Modifier
                        .padding(end = 8.dp, bottom = 5.dp)
                        .size(20.dp)
                )
            }
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ProaBluePrimary,
                softWrap = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}
