package com.notivault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.notivault.ui.motion.rememberPressMorphShape
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault

const val PIN_LENGTH = 6

@Composable
fun PinDots(filled: Int, error: Boolean, shakeKey: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
    }
    Row(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        repeat(PIN_LENGTH) { i ->
            val on = i < filled
            val color by animateColorAsState(
                when { error -> Vault.colors.danger; on -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.surfaceContainerHigh },
                label = "dot",
            )
            Box(Modifier.size(14.dp).clip(CircleShape).background(color)
                .border(1.dp, if (on || error) color else MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
fun PinPad(onDigit: (Int) -> Unit, onBackspace: () -> Unit, onBiometric: (() -> Unit)? = null, keySize: Dp = 72.dp) {
    val haptics = LocalHapticFeedback.current
    val keys = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(verticalArrangement = Arrangement.spacedBy(keySize / 6), horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySize / 3)) {
                row.forEach { k ->
                    when (k) {
                        -1 -> if (onBiometric != null) PadKey(keySize, onClick = { onBiometric() }, label = "Use biometrics") {
                            Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                        } else Box(Modifier.size(keySize))
                        -2 -> PadKey(keySize, onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onBackspace() }, label = "Delete") {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, null)
                        }
                        else -> PadKey(keySize, onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDigit(k) }, label = "$k") {
                            Text("$k", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(size: Dp, onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { contentDescription = label },
        shape = rememberPressMorphShape(interaction),
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Key size that fits a 4-row keypad into about half of [screenHeight]. */
fun pinKeySize(screenHeight: Dp): Dp = (screenHeight * 0.095f).coerceIn(52.dp, 76.dp)
