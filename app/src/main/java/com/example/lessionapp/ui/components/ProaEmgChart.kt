package com.example.lessionapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.lessionapp.ui.theme.ProaBlueSecondary
import com.example.lessionapp.ui.theme.ProaChartGrid
import com.example.lessionapp.ui.theme.ProaCyanAccent

/**
 * Componente gráfico en tiempo real para visualizar la señal EMG V3 usando Canvas y los colores PROA (HU-04).
 */
@Composable
fun ProaEmgChart(
    dataPoints: List<Double>,
    modifier: Modifier = Modifier,
    maxValue: Double = 3300.0 // Valor máximo de referencia (mV)
) {
    val gridColor = ProaChartGrid
    val lineColor = ProaBlueSecondary
    val gradientColorStart = ProaCyanAccent.copy(alpha = 0.4f)
    val gradientColorEnd = ProaCyanAccent.copy(alpha = 0.0f)

    ProaCard(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height

            // 1. Dibujar Cuadrícula de Fondo (4 líneas horizontales, 5 verticales)
            val rows = 4
            val cols = 5
            for (i in 0..rows) {
                val y = (height / rows) * i
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (j in 0..cols) {
                val x = (width / cols) * j
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (dataPoints.size < 2) return@Canvas

            // 2. Calcular puntos de la curva EMG
            val safeMax = if (maxValue <= 0.0) 3300.0 else maxValue
            val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

            val path = Path()
            val fillPath = Path()

            fillPath.moveTo(0f, height)

            dataPoints.forEachIndexed { index, value ->
                val x = index * stepX
                val normalizedY = (value / safeMax).coerceIn(0.0, 1.0)
                val y = height - (normalizedY * height).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // 3. Dibujar Gradiente bajo la curva
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(gradientColorStart, gradientColorEnd),
                    startY = 0f,
                    endY = height
                )
            )

            // 4. Dibujar Línea de la Señal EMG
            drawPath(
                path = path,
                brush = SolidColor(lineColor),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
