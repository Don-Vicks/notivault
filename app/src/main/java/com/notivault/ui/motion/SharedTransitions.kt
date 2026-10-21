package com.notivault.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/** Provided by the nav shell; null in previews and outside navigation, where modifiers become no-ops. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val VaultBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) }

/** Container transform: the element morphs its bounds and shape into the matching element on the next screen. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.vaultSharedBounds(key: Any, shape: Shape = RectangleShape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@vaultSharedBounds.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = VaultBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Identical content (avatar, title) that flies between screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.vaultSharedElement(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@vaultSharedElement.sharedElement(rememberSharedContentState(key), anim, boundsTransform = VaultBounds)
    }
}
