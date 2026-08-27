package com.codeaza.bhaiyaaa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One labelled bar. */
data class Bar(val label: String, val value: Int)

/**
 * A small bar chart drawn with Compose Canvas.
 *
 * Hand-drawn rather than pulling in a charting library: this is the only chart
 * in the app, and a dependency would add far more to the APK and the build than
 * thirty lines of drawing code.
 *
 * The whole chart carries one content description listing the values, so
 * TalkBack users get the data rather than an unlabelled graphic.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Int = 132
) {
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.value }.coerceAtLeast(1)
    val emptyTrack = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val description = bars.joinToString(", ") { "${it.label}: ${it.value}" }

    Column(modifier.semantics { contentDescription = "Calls per day. $description" }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
        ) {
            val count = bars.size
            val slot = size.width / count
            // Leave a third of each slot as gap so bars read as separate columns.
            val barWidth = slot * 0.62f
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)

            bars.forEachIndexed { index, bar ->
                val left = index * slot + (slot - barWidth) / 2f
                val fraction = bar.value.toFloat() / maxValue
                val barHeight = size.height * fraction

                // Track behind every bar, so a zero day is still visibly a day.
                drawRoundRect(
                    color = emptyTrack,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                if (bar.value > 0) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                ) {
                    Text(
                        text = bar.value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Two-segment proportion bar (incoming vs outgoing). Percentages are printed
 * next to it, so the split is never conveyed by colour alone.
 */
@Composable
fun SplitBar(
    leftValue: Int,
    rightValue: Int,
    leftColor: Color,
    rightColor: Color,
    modifier: Modifier = Modifier
) {
    val total = (leftValue + rightValue).coerceAtLeast(1)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        val leftWidth = size.width * (leftValue.toFloat() / total)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = rightColor, size = size, cornerRadius = radius)
        if (leftValue > 0) {
            drawRoundRect(
                color = leftColor,
                size = Size(leftWidth, size.height),
                cornerRadius = radius
            )
        }
    }
}
