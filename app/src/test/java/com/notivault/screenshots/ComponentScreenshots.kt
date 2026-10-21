package com.notivault.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.resources.NightMode
import com.notivault.data.db.MessageRow
import com.notivault.data.prefs.ThemeMode
import com.notivault.ui.components.ChatBubble
import com.notivault.ui.components.MessageCard
import com.notivault.ui.components.PinDots
import com.notivault.ui.components.RecoveredBadge
import com.notivault.ui.components.StatTile
import com.notivault.ui.receipt.ReceiptCard
import com.notivault.ui.theme.NotiVaultTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Visual regression tests for the Vault design system in light, dark and 1.6× font scale. */
@RunWith(Parameterized::class)
class ComponentScreenshots(private val variant: Variant) {

    enum class Variant(val dark: Boolean, val fontScale: Float) { Light(false, 1f), Dark(true, 1f), LargeFont(false, 1.6f) }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun variants() = Variant.entries.toList()

        private const val TS = 1_792_576_960_000L // fixed clock so snapshots are stable
        fun row(id: Long, sender: String, text: String, deleted: Boolean = false, unsent: Boolean = false, kind: String = "Text") = MessageRow(
            id = id, conversationId = 1, packageName = "com.whatsapp", sender = sender, text = text,
            postedAt = TS, msgTimestamp = TS, hash = "d3c42497cc2dbb3971fe09863478d5659c7858111e27eeed1e8d67beac7e3e5a",
            deletedBySender = deleted, deletedDetectedAt = if (deleted) TS + 2000 else null, attachmentPath = null,
            kind = kind, possiblyUnsentAt = if (unsent) TS else null, conversationTitle = "Squad", isGroup = true, appLabel = "WhatsApp",
        )
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            nightMode = if (variant.dark) NightMode.NIGHT else NightMode.NOTNIGHT,
            fontScale = variant.fontScale,
        ),
        // Small tolerance for anti-aliasing differences between macOS and the Linux CI runner.
        maxPercentDifference = 0.5,
    )

    private fun snap(content: @Composable () -> Unit) = paparazzi.snapshot {
        NotiVaultTheme(mode = if (variant.dark) ThemeMode.Dark else ThemeMode.Light) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }

    @Test fun messageCards() = snap {
        MessageCard(row(1, "Ken", "Bring the documents"), onClick = {})
        MessageCard(row(2, "Tunde", "I owe you 50k, will pay Friday", deleted = true), onClick = {})
        MessageCard(row(3, "Ada", "📷 Photo", unsent = true, kind = "Photo"), onClick = {})
    }

    @Test fun chatBubbles() = snap {
        ChatBubble(row(1, "Ada", "Meet at the cafe at 6"), showSender = true, onClick = {})
        ChatBubble(row(2, "Tunde", "I owe you 50k, will pay Friday", deleted = true), showSender = true, onClick = {})
    }

    @Test fun receipt() = snap { ReceiptCard(row(12, "Tunde", "I owe you 50k, will pay Friday", deleted = true)) }

    @Test fun tokens() = snap {
        StatTile("1.2k", "Saved"); RecoveredBadge(); PinDots(filled = 3, error = false, shakeKey = 0)
    }
}
