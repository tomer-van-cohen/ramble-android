package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Original designer wave geometry, painted natively to avoid embedded-browser scaling/layer issues. */
@Composable
fun BrandWave(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active) {
        if (active) {
            val resumeAt = withFrameNanos { it }
            val offset = elapsed
            while (true) withFrameNanos { elapsed = offset + (it - resumeAt) / 1_000_000_000f }
        }
    }
    Canvas(modifier.testTag("welcome-wave")) {
        val reveal = 1f - (1f - min(1f, elapsed / 1.1f)).pow(3)
        val end = -20f + 760f * reveal
        for (line in 0..1) {
            val base = if (line == 0) 88f else 104f
            val amplitude = if (line == 0) 1f else .7f
            val shift = if (line == 0) 0f else .9f
            val path = Path()
            var first = true
            var x = -20f
            while (x <= end + .01f) {
                val u = (x + 20f) / 760f
                val big = exp(-u * 3.4f) * (.88f + .12f * sin(elapsed * 1.8f + shift))
                val phase = u * 21f + u * u * 16f + shift * .3f
                val calm = .15f * sin(u * 34f + u * u * 12f - elapsed * 3.4f + shift)
                val grow = min(1f, (end - x) / 60f + .15f)
                val y = base - 70f * amplitude * (big * sin(phase) + calm) * grow
                val px = x / 720f * size.width
                val py = y / 170f * size.height
                if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                x += 5f
            }
            drawPath(path, Color(0xFF25D366).copy(alpha = if (line == 0) 1f else .35f),
                style = Stroke(width = size.width / 720f * if (line == 0) 7f else 3f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
