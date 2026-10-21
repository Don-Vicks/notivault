package com.notivault.ui.theme

/**
 * Raw Vault colour values (ARGB). Kept free of Compose so the WCAG contrast test runs on the JVM.
 * Amber is reserved exclusively for "recovered / deleted by sender".
 */
object Palette {
    object Dark {
        const val background = 0xFF0B0D12
        const val surface = 0xFF14171F
        const val surfaceRaised = 0xFF1C2030
        const val surfaceHigh = 0xFF252A3D
        const val outline = 0xFF2E3448
        const val primary = 0xFF7C8CFF
        const val onPrimary = 0xFF0B0D12
        const val primaryContainer = 0xFF262D63
        const val onPrimaryContainer = 0xFFDDE1FF
        const val recovered = 0xFFF5B544
        const val onRecovered = 0xFF1F1400
        const val recoveredContainer = 0xFF3A2A08
        const val onRecoveredContainer = 0xFFFFDDA6
        const val success = 0xFF3DD68C
        const val danger = 0xFFFF6B6B
        const val textPrimary = 0xEBFFFFFF   // 92%
        const val textSecondary = 0xA3FFFFFF // 64%
    }

    object Light {
        const val background = 0xFFF7F7FA
        const val surface = 0xFFFFFFFF
        const val surfaceRaised = 0xFFF0F1F5
        const val surfaceHigh = 0xFFE6E8EF
        const val outline = 0xFFD5D8E2
        const val primary = 0xFF4453E0
        const val onPrimary = 0xFFFFFFFF
        const val primaryContainer = 0xFFDFE2FF
        const val onPrimaryContainer = 0xFF141B66
        const val recovered = 0xFFB87400
        const val onRecovered = 0xFFFFFFFF
        const val recoveredContainer = 0xFFFFEBC7
        const val onRecoveredContainer = 0xFF3D2700
        const val success = 0xFF0E8A52
        const val danger = 0xFFC62F2F
        const val textPrimary = 0xEB0B0D12
        const val textSecondary = 0xA30B0D12
    }

    /** WCAG 2.1 contrast ratio of [fg] composited over opaque [bg]. */
    fun contrast(fg: Long, bg: Long): Double {
        val a = ((fg shr 24) and 0xFF) / 255.0
        fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF) / 255.0
        fun blend(shift: Int) = ch(fg, shift) * a + ch(bg, shift) * (1 - a)
        fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        fun lum(r: Double, g: Double, b: Double) = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        val l1 = lum(blend(16), blend(8), blend(0))
        val l2 = lum(ch(bg, 16), ch(bg, 8), ch(bg, 0))
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
}
