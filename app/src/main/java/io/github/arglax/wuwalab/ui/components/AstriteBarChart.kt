package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.AstriteStats
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary

/**
 * Deliberately hand-rolled with plain Canvas instead of a charting library -
 * this is the entire chart, ~60 lines, and it keeps the app footprint small
 * for what's fundamentally a handful of bars.
 */
@Composable
fun AstriteBarChart(
    buckets: List<AstriteStats.Bucket>,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
    showLabels: Boolean = true
) {
    val maxValue = (buckets.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            if (buckets.isEmpty()) return@Canvas
            val barCount = buckets.size
            val spacing = size.width * 0.02f
            val barWidth = (size.width - spacing * (barCount + 1)) / barCount

            // Faint horizontal gridlines for a quick sense of scale.
            val gridColor = Color.White.copy(alpha = 0.06f)
            for (i in 0..3) {
                val y = size.height * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            buckets.forEachIndexed { index, bucket ->
                val barHeight = if (maxValue == 0) 0f else (bucket.total / maxValue.toFloat()) * size.height * 0.92f
                val x = spacing + index * (barWidth + spacing)
                val top = size.height - barHeight
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accent, accent.copy(alpha = 0.35f)),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(x, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }
        if (showLabels) BarChartLabelsRow(buckets)
    }
}

@Composable
private fun BarChartLabelsRow(buckets: List<AstriteStats.Bucket>) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        buckets.forEach { bucket ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    bucket.label,
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    maxLines = 1
                )
            }
        }
    }
}