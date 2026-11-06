package io.github.tomervancohen.rambleflow

import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Physical Side key remains on the right, including in Hebrew RTL. Coordinates from prototype. */
@Composable
fun SideButtonArt(height: Dp = 180.dp) {
    val colors = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "Ramble wave")
    val phase by motion.animateFloat(0f, (Math.PI * 2).toFloat(),
        infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "Wave phase")
    Canvas(Modifier.fillMaxWidth().height(height).testTag("side-button-art")) {
        val scale = size.height / 190f
        val left = (size.width - 110f * scale) / 2f
        fun pt(x: Float, y: Float) = Offset(left + x * scale, (y + 10) * scale)
        drawRoundRect(
            colors.surface,
            pt(0f, 0f),
            Size(110 * scale, 170 * scale),
            CornerRadius(18 * scale),
        )
        drawRoundRect(
            colors.onSurface,
            pt(0f, 0f),
            Size(110 * scale, 170 * scale),
            CornerRadius(18 * scale),
            style = Stroke(3 * scale),
        )
        drawRoundRect(
            colors.surfaceVariant,
            pt(10f, 12f),
            Size(90 * scale, 140 * scale),
            CornerRadius(10 * scale),
        )
        // The actual Ramble mark's four humps, with gentle staggered motion.
        val wave = Path().apply {
            val origin = pt(19f, 82f)
            moveTo(origin.x, origin.y)
            for ((index, hump) in listOf(-36f to 13.5f, 30f to 13.5f, -19.5f to 10.5f, 12f to 10.5f).withIndex()) {
                val amplitude = .82f + .18f * sin(phase + index * .9f)
                relativeQuadraticBezierTo(hump.second * scale / 2, hump.first * amplitude * scale,
                    hump.second * scale, 0f)
            }
            relativeLineTo(24 * scale, 0f)
        }
        drawPath(wave, Color(0xFF25D366), style = Stroke(4 * scale, cap = StrokeCap.Round))
        drawRoundRect(
            Color(0xFF25D366),
            pt(110f, 42f),
            Size(7 * scale, 32 * scale),
            CornerRadius(3 * scale),
        )
        for ((x, y, w, h) in listOf(listOf(126f, 44f, 10f, 22f), listOf(138f, 37f, 16f, 34f))) {
            val start = pt(x, y)
            drawPath(
                Path().apply {
                    moveTo(start.x, start.y)
                    relativeQuadraticBezierTo(w * scale, h * scale / 2, 0f, h * scale)
                },
                colors.secondary,
                style = Stroke(3 * scale),
            )
        }
    }
}
