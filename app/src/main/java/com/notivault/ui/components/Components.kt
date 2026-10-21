package com.notivault.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneMissed
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.notivault.capture.MessageKind
import com.notivault.data.db.MessageRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.theme.Motion
import com.notivault.ui.theme.MonoStyle
import com.notivault.ui.theme.Radius
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault

// ---------------------------------------------------------------- avatar

/** Source-app icon with a sender-initial ring. Falls back to a tonal monogram. */
@Composable
fun AppAvatar(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 44.dp, sender: String? = null) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, contentDescription = label, modifier = Modifier.size(size * 0.62f))
            } else {
                Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (sender != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.42f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sender.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- badges

@Composable
fun RecoveredBadge(modifier: Modifier = Modifier, pulse: Boolean = false, compact: Boolean = false) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    Row(
        modifier
            .semantics { contentDescription = "Deleted by sender, recovered" }
            .clip(RoundedCornerShape(50))
            .background(Vault.colors.recoveredContainer)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.DeleteSweep, null, tint = Vault.colors.recovered, modifier = Modifier.size(14.dp).alpha(alpha))
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            Text("Recovered", style = MaterialTheme.typography.labelSmall, color = Vault.colors.onRecoveredContainer)
        }
    }
}

fun kindIcon(kind: MessageKind): ImageVector = when (kind) {
    MessageKind.Text -> Icons.Outlined.ChatBubbleOutline
    MessageKind.Photo -> Icons.Outlined.Image
    MessageKind.Video -> Icons.Outlined.Videocam
    MessageKind.Voice -> Icons.Outlined.Mic
    MessageKind.Audio -> Icons.Outlined.MusicNote
    MessageKind.Sticker -> Icons.Outlined.EmojiEmotions
    MessageKind.Gif -> Icons.Outlined.Gif
    MessageKind.Document -> Icons.Outlined.Description
    MessageKind.Location -> Icons.Outlined.Place
    MessageKind.Contact -> Icons.Outlined.ContactPage
    MessageKind.Poll -> Icons.Outlined.Poll
    MessageKind.Call -> Icons.Outlined.PhoneMissed
    MessageKind.Reaction -> Icons.Outlined.FavoriteBorder
}

/** Media/event label for non-text captures, plus a hint when a preview image was saved. */
@Composable
fun KindHint(kind: String, hasPreview: Boolean) {
    val k = runCatching { MessageKind.valueOf(kind) }.getOrDefault(MessageKind.Text)
    if (k == MessageKind.Text && !hasPreview) return
    val shown = if (k == MessageKind.Text) MessageKind.Photo else k
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(kindIcon(shown), null, Modifier.size(14.dp), tint = Vault.colors.textSecondary)
        Spacer(Modifier.width(4.dp))
        Text(if (hasPreview) "${shown.label} preview" else shown.label,
            style = MaterialTheme.typography.labelSmall, color = Vault.colors.textSecondary)
    }
}

/** Weaker than [RecoveredBadge]: the app withdrew the notification and it might have been unsent. */
@Composable
fun PossiblyUnsentBadge(modifier: Modifier = Modifier) {
    Text(
        "Possibly unsent",
        style = MaterialTheme.typography.labelSmall,
        color = Vault.colors.recovered,
        modifier = modifier
            .semantics { contentDescription = "Possibly unsent by the sender" }
            .border(1.dp, Vault.colors.recovered.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** One spoken sentence per card for TalkBack instead of reading each fragment. */
fun spokenSummary(row: MessageRow): String = buildString {
    if (row.deletedBySender) append("Recovered deleted message. ") else if (row.possiblyUnsentAt != null) append("Possibly unsent message. ")
    append("From ").append(row.sender)
    if (row.sender != row.conversationTitle) append(" in ").append(row.conversationTitle)
    append(", on ").append(row.appLabel).append(", ").append(Format.relative(row.msgTimestamp)).append(". ")
    append(row.text)
}

// ---------------------------------------------------------------- message card

@Composable
fun MessageCard(row: MessageRow, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: String? = null) {
    val recovered = row.deletedBySender
    Surface(
        onClick = onClick,
        modifier = modifier.vaultSharedBounds("msg-${row.id}", MaterialTheme.shapes.medium).fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spokenSummary(row); role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (recovered) androidx.compose.foundation.BorderStroke(1.dp, Vault.colors.recovered.copy(alpha = 0.45f)) else null,
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
            AppAvatar(row.packageName, row.appLabel, sender = row.sender)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (row.isGroup || row.sender != row.conversationTitle) row.conversationTitle else row.sender,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (recovered) { Spacer(Modifier.width(Space.xs)); RecoveredBadge(compact = true) }
                    else if (row.possiblyUnsentAt != null) { Spacer(Modifier.width(Space.xs)); PossiblyUnsentBadge() }
                    Spacer(Modifier.weight(1f))
                    Text(Format.relative(row.msgTimestamp), style = MaterialTheme.typography.labelSmall, color = Vault.colors.textSecondary)
                }
                Spacer(Modifier.height(2.dp))
                if (row.isGroup || row.sender != row.conversationTitle) {
                    Text(row.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                Text(
                    highlighted(row.text, highlight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.appLabel, style = MaterialTheme.typography.labelSmall, color = Vault.colors.textSecondary)
                    Spacer(Modifier.width(Space.sm)); KindHint(row.kind, row.attachmentPath != null)
                }
            }
        }
    }
}

@Composable
fun highlighted(text: String, query: String?): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.primaryContainer
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
    return buildAnnotatedString {
        append(text)
        terms.forEach { term ->
            var i = text.indexOf(term, ignoreCase = true)
            while (i >= 0) {
                addStyle(SpanStyle(color = color, background = bg, fontWeight = FontWeight.SemiBold), i, i + term.length)
                i = text.indexOf(term, i + term.length, ignoreCase = true)
            }
        }
    }
}

// ---------------------------------------------------------------- chat bubble

@Composable
fun ChatBubble(row: MessageRow, showSender: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val recovered = row.deletedBySender
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = Radius.card, bottomEnd = Radius.card, bottomStart = Radius.card)
    Column(modifier.fillMaxWidth().padding(end = 48.dp)) {
        if (showSender) {
            Text(row.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.sm, bottom = 2.dp))
        }
        Surface(
            onClick = onClick,
            shape = shape,
            color = if (recovered) Vault.colors.recoveredContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (recovered) androidx.compose.foundation.BorderStroke(1.dp, Vault.colors.recovered.copy(alpha = 0.6f)) else null,
            modifier = Modifier.vaultSharedBounds("msg-${row.id}", shape).widthIn(max = 320.dp)
                .clearAndSetSemantics { contentDescription = spokenSummary(row); role = Role.Button },
        ) {
            Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
                if (recovered) { RecoveredBadge(pulse = false); Spacer(Modifier.height(Space.xxs)) }
                else if (row.possiblyUnsentAt != null) { PossiblyUnsentBadge(); Spacer(Modifier.height(Space.xxs)) }
                Text(row.text, style = MaterialTheme.typography.bodyLarge,
                    color = if (recovered) Vault.colors.onRecoveredContainer else MaterialTheme.colorScheme.onSurface)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    KindHint(row.kind, row.attachmentPath != null); Spacer(Modifier.width(Space.xs))
                    Text(Format.time(row.msgTimestamp), style = MonoStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = Vault.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun DayDivider(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = Space.sm), contentAlignment = Alignment.Center) {
        Text(
            Format.dayHeader(ts),
            style = MaterialTheme.typography.labelSmall,
            color = Vault.colors.textSecondary,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = Space.sm, vertical = Space.xxs),
        )
    }
}

// ---------------------------------------------------------------- chips & search

data class ChipOption(val key: String?, val label: String)

@Composable
fun FilterChipRow(options: List<ChipOption>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier,
                  leading: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        options.forEach { o ->
            FilterChip(
                selected = o.key == selected,
                onClick = { onSelect(o.key) },
                label = { Text(o.label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search messages, people…") {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = Vault.colors.textSecondary)
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Vault.colors.textSecondary)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, "Clear search") }
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, Color.Transparent)),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Space.lg)); action() }
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier, lines: Int = 6) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        repeat(lines) {
            Box(Modifier.fillMaxWidth().height(84.dp).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = a)))
        }
    }
}

@Composable
fun HealthBanner(onFix: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(horizontal = Space.md), shape = MaterialTheme.shapes.medium,
        color = Vault.colors.danger.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Vault.colors.danger)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text("Capture is paused", style = MaterialTheme.typography.titleSmall)
                Text("Notification access is off, so new messages aren't being saved.",
                    style = MaterialTheme.typography.bodySmall, color = Vault.colors.textSecondary)
            }
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

@Composable
fun PermissionCard(icon: ImageVector, title: String, body: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (granted) Vault.colors.success.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Vault.colors.textSecondary)
            }
            Spacer(Modifier.width(Space.xs))
            if (granted) Text("Done", style = MaterialTheme.typography.labelLarge, color = Vault.colors.success)
            else TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() }.padding(start = Space.md, end = Space.md, top = Space.lg, bottom = Space.xs))
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null,
               trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Vault.colors.textSecondary)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vault.colors.textSecondary)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Vault.colors.textSecondary)
        }
    }
}

@Composable
fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Vault.colors.textSecondary)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vault.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier.clearAndSetSemantics { contentDescription = "$value $label" }, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Space.md)) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(tween(Motion.emphasized, easing = Motion.EmphasizedEasing)) { it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(Motion.standard)) { -it } + fadeOut())
                },
                label = "stat",
            ) { v -> Text(v, style = MaterialTheme.typography.headlineSmall, color = accent) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = Vault.colors.textSecondary)
        }
    }
}
