package com.notivault.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.notivault.ui.theme.Motion
import com.notivault.ui.theme.Vault

/** Animated brand mark: a shield with a padlock whose shackle closes when [locked]. */
@Composable
fun VaultMark(size: Int = 120, locked: Boolean = true, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val amber = Vault.colors.recovered
    val bg = MaterialTheme.colorScheme.background
    val t = rememberInfiniteTransition(label = "mark")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbit")
    val glow by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    val shackle by animateFloatAsState(if (locked) 0f else 1f, tween(Motion.emphasized, easing = Motion.EmphasizedEasing), label = "shackle")

    Canvas(modifier.size(size.dp)) {
        val w = this.size.width; val h = this.size.height; val cx = w / 2
        drawCircle(Brush.radialGradient(listOf(container.copy(alpha = glow), Color.Transparent)), radius = w / 2)
        rotate(orbit) {
            drawArc(primary.copy(alpha = 0.5f), 0f, 70f, false, topLeft = Offset(w * .06f, h * .06f),
                size = Size(w * .88f, h * .88f), style = Stroke(w * .015f, cap = StrokeCap.Round))
            drawArc(amber.copy(alpha = 0.7f), 180f, 30f, false, topLeft = Offset(w * .06f, h * .06f),
                size = Size(w * .88f, h * .88f), style = Stroke(w * .015f, cap = StrokeCap.Round))
        }
        val shield = Path().apply {
            moveTo(cx, h * .2f); lineTo(w * .72f, h * .3f); lineTo(w * .72f, h * .5f)
            cubicTo(w * .72f, h * .66f, w * .62f, h * .76f, cx, h * .82f)
            cubicTo(w * .38f, h * .76f, w * .28f, h * .66f, w * .28f, h * .5f)
            lineTo(w * .28f, h * .3f); close()
        }
        drawPath(shield, Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.75f))))
        // padlock
        val bodyTop = h * .5f
        drawRoundRect(bg, topLeft = Offset(cx - w * .1f, bodyTop), size = Size(w * .2f, h * .15f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .03f))
        val lift = shackle * h * .05f
        drawArc(bg, 180f, 180f, false, topLeft = Offset(cx - w * .065f, bodyTop - h * .1f - lift),
            size = Size(w * .13f, h * .16f), style = Stroke(w * .03f, cap = StrokeCap.Round))
        drawCircle(amber, radius = w * .022f, center = Offset(cx, bodyTop + h * .07f))
    }
}
