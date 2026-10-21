package com.notivault.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/** Vault's shape vocabulary. All normalised to a 0..1 box so they morph cleanly into each other. */
object VaultPolygons {
    val circle = RoundedPolygon.circle(numVertices = 12).normalized()
    val cookie = RoundedPolygon.star(9, innerRadius = 0.86f, rounding = CornerRounding(0.35f), innerRounding = CornerRounding(0.35f)).normalized()
    val clover = RoundedPolygon.star(4, innerRadius = 0.62f, rounding = CornerRounding(0.45f), innerRounding = CornerRounding(0.2f)).normalized()
    val squircle = RoundedPolygon(4, rounding = CornerRounding(0.42f)).normalized()
    val flower = RoundedPolygon.star(6, innerRadius = 0.72f, rounding = CornerRounding(0.4f), innerRounding = CornerRounding(0.25f)).normalized()
    val pentagon = RoundedPolygon(5, rounding = CornerRounding(0.3f)).normalized()

    /** Sequence the onboarding tiles walk through. */
    val tour = listOf(squircle, flower, clover, cookie)
}

/** A [Shape] that renders a [Morph] at a given progress, scaled to the layout box. */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val m = android.graphics.Matrix().apply {
            setScale(size.width, size.height)
            if (rotation != 0f) postRotate(rotation, size.width / 2, size.height / 2)
        }
        path.transform(m)
        return Outline.Generic(path.asComposePath())
    }
}

/** Circle that springs into a scalloped cookie while pressed. */
@Composable
fun rememberPressMorphShape(interaction: InteractionSource): Shape {
    val morph = remember { Morph(VaultPolygons.circle, VaultPolygons.cookie) }
    val pressed by interaction.collectIsPressedAsState()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        progress.animateTo(if (pressed) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    return MorphShape(morph, progress.value)
}

/** Shape that continuously breathes between two polygons and slowly turns, for hero tiles. */
@Composable
fun rememberBreathingShape(from: RoundedPolygon, to: RoundedPolygon, periodMs: Int = 2600): Shape {
    val morph = remember(from, to) { Morph(from, to) }
    val t = rememberInfiniteTransition(label = "breathe")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "p")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs * 8, easing = LinearEasing)), label = "r")
    return MorphShape(morph, p, r)
}

/** Morphs from the previous polygon to [target] whenever it changes. */
@Composable
fun rememberMorphTo(target: RoundedPolygon): Shape {
    val last = remember { arrayOf(target) }
    val progress = remember { Animatable(1f) }
    val morph = remember(target) { Morph(last[0], target) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 180f))
        last[0] = target
    }
    return MorphShape(morph, progress.value)
}
