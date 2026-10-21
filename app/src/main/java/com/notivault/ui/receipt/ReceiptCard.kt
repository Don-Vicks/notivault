package com.notivault.ui.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notivault.data.db.MessageRow
import com.notivault.ui.components.Format
import com.notivault.ui.theme.MonoStyle
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault

/** Paper-receipt presentation of a single captured message. Mirrors [ReceiptRenderer]'s exported layout. */
@Composable
fun ReceiptCard(row: MessageRow, modifier: Modifier = Modifier) {
    val paper = if (Vault.colors.isDark) Color(0xFF1A1E2B) else Color(0xFFFFFDF8)
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = Vault.colors.textSecondary
    val amber = Vault.colors.recovered
    val bg = MaterialTheme.colorScheme.background

    Box(
        modifier.fillMaxWidth().drawBehind {
            // Paper with perforated top & bottom edges.
            drawRoundRect(paper, cornerRadius = CornerRadius(6.dp.toPx()))
            val r = 6.dp.toPx(); val step = 18.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                drawCircle(bg, r, Offset(x, 0f)); drawCircle(bg, r, Offset(x, size.height)); x += step
            }
        }.padding(horizontal = Space.lg, vertical = Space.xl),
    ) {
        Column {
            Text("NOTIVAULT · MESSAGE RECEIPT", style = MonoStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), color = ink)
            Text("Captured on-device from a notification", style = MonoStyle.copy(fontSize = 11.sp), color = muted)
            Dashes(muted)
            Field("APP", row.appLabel, ink, muted)
            Field("CHAT", row.conversationTitle + if (row.isGroup) " (group)" else "", ink, muted)
            Field("FROM", row.sender, ink, muted)
            Field("SENT", Format.precise(row.msgTimestamp), ink, muted)
            Field("CAPTURED", Format.precise(row.postedAt), ink, muted)
            if (row.deletedBySender && row.deletedDetectedAt != null) Field("DELETED", Format.precise(row.deletedDetectedAt), amber, muted)
            if (!row.deletedBySender && row.possiblyUnsentAt != null) Field("WITHDRAWN", Format.precise(row.possiblyUnsentAt) + " (possibly unsent)", amber, muted)
            Text("Times in ${Format.zone(row.msgTimestamp)}", style = MonoStyle.copy(fontSize = 11.sp), color = muted,
                modifier = Modifier.padding(top = 4.dp))
            Dashes(muted)
            Text(row.text, style = MaterialTheme.typography.bodyLarge, color = ink)
            Dashes(muted)
            Text("SHA-256", style = MonoStyle.copy(fontSize = 11.sp), color = muted)
            Text(row.hash.chunked(8).joinToString(" "), style = MonoStyle.copy(fontSize = 11.sp), color = ink)
            Spacer(Modifier.height(Space.xs))
            Text("ID #${row.id.toString().padStart(8, '0')}", style = MonoStyle.copy(fontSize = 11.sp), color = muted)
        }
        if (row.deletedBySender) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp).rotate(-12f)
                    .drawBehind {
                        drawRoundRect(amber, size = Size(size.width, size.height), cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 4f))))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("RECOVERED", style = MonoStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp), color = amber)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, ink: Color, muted: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        // Width in sp so the label column grows with the user's font size instead of wrapping.
        val labelWidth = with(LocalDensity.current) { 108.sp.toDp() }
        Text(label, style = MonoStyle, color = muted, maxLines = 1, softWrap = false, modifier = Modifier.width(labelWidth))
        Text(value, style = MonoStyle, color = ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Dashes(color: Color) {
    Canvas(Modifier.fillMaxWidth().padding(vertical = Space.sm).height(1.dp)) {
        drawLine(color.copy(alpha = 0.5f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

