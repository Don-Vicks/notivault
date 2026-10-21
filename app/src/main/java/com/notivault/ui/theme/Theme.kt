package com.notivault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.notivault.data.prefs.ThemeMode

/** Semantic colours Material doesn't model. Never overridden by dynamic colour. */
@Immutable
data class VaultColors(
    val recovered: Color,
    val onRecovered: Color,
    val recoveredContainer: Color,
    val onRecoveredContainer: Color,
    val success: Color,
    val danger: Color,
    val textSecondary: Color,
    val surfaceRaised: Color,
    val isDark: Boolean,
)

private fun c(v: Long) = Color(v)

private val darkVault = with(Palette.Dark) {
    VaultColors(c(recovered), c(onRecovered), c(recoveredContainer), c(onRecoveredContainer), c(success), c(danger), c(textSecondary), c(surfaceRaised), true)
}
private val lightVault = with(Palette.Light) {
    VaultColors(c(recovered), c(onRecovered), c(recoveredContainer), c(onRecoveredContainer), c(success), c(danger), c(textSecondary), c(surfaceRaised), false)
}

private val darkScheme = with(Palette.Dark) {
    darkColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(primary), onSecondary = c(onPrimary),
        secondaryContainer = c(surfaceHigh), onSecondaryContainer = c(textPrimary),
        background = c(background), onBackground = c(textPrimary),
        surface = c(background), onSurface = c(textPrimary),
        surfaceVariant = c(surfaceRaised), onSurfaceVariant = c(textSecondary),
        surfaceContainerLowest = c(background), surfaceContainerLow = c(surface),
        surfaceContainer = c(surface), surfaceContainerHigh = c(surfaceRaised), surfaceContainerHighest = c(surfaceHigh),
        outline = c(outline), outlineVariant = c(outline),
        error = c(danger), onError = c(onPrimary),
    )
}
private val lightScheme = with(Palette.Light) {
    lightColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(primary), onSecondary = c(onPrimary),
        secondaryContainer = c(surfaceHigh), onSecondaryContainer = c(textPrimary),
        background = c(background), onBackground = c(textPrimary),
        surface = c(background), onSurface = c(textPrimary),
        surfaceVariant = c(surfaceRaised), onSurfaceVariant = c(textSecondary),
        surfaceContainerLowest = c(surface), surfaceContainerLow = c(surface),
        surfaceContainer = c(surface), surfaceContainerHigh = c(surfaceRaised), surfaceContainerHighest = c(surfaceHigh),
        outline = c(outline), outlineVariant = c(outline),
        error = c(danger), onError = c(onPrimary),
    )
}

val LocalVaultColors = staticCompositionLocalOf { darkVault }

object Vault {
    val colors: VaultColors @Composable get() = LocalVaultColors.current
}

@Composable
fun NotiVaultTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkScheme
        else -> lightScheme
    }
    CompositionLocalProvider(LocalVaultColors provides if (dark) darkVault else lightVault) {
        MaterialTheme(colorScheme = scheme, typography = VaultTypography, shapes = VaultShapes, content = content)
    }
}
