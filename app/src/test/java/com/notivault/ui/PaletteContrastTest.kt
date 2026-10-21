package com.notivault.ui

import com.notivault.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the design system: every text/background pairing must meet WCAG AA (4.5:1). */
class PaletteContrastTest {
    private fun check(name: String, fg: Long, bg: Long, min: Double = 4.5) {
        val r = Palette.contrast(fg, bg)
        assertTrue("$name contrast %.2f < $min".format(r), r >= min)
    }

    @Test fun dark() = with(Palette.Dark) {
        for ((n, bg) in listOf("background" to background, "surface" to surface, "raised" to surfaceRaised)) {
            check("text/$n", textPrimary, bg)
            check("secondary/$n", textSecondary, bg)
            check("primary/$n", primary, bg)
            check("recovered/$n", recovered, bg)
        }
        check("onPrimary", onPrimary, primary)
        check("onRecoveredContainer", onRecoveredContainer, recoveredContainer)
        check("onPrimaryContainer", onPrimaryContainer, primaryContainer)
    }

    @Test fun light() = with(Palette.Light) {
        for ((n, bg) in listOf("background" to background, "surface" to surface, "raised" to surfaceRaised)) {
            check("text/$n", textPrimary, bg)
            check("secondary/$n", textSecondary, bg)
            check("primary/$n", primary, bg)
            check("recovered/$n", recovered, bg, min = 3.0) // used for icons/large labels in light mode
        }
        check("onPrimary", onPrimary, primary)
        check("onRecoveredContainer", onRecoveredContainer, recoveredContainer)
        check("onPrimaryContainer", onPrimaryContainer, primaryContainer)
    }
}
