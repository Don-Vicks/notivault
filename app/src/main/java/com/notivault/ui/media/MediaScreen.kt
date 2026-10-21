package com.notivault.ui.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.notivault.data.db.MediaEntity
import com.notivault.data.db.MediaStats
import com.notivault.data.media.MediaRules
import com.notivault.data.media.MediaType
import com.notivault.data.media.MediaVault
import com.notivault.data.prefs.SettingsStore
import com.notivault.ui.components.ChipOption
import com.notivault.ui.components.EmptyState
import com.notivault.ui.components.FilterChipRow
import com.notivault.ui.components.Format
import com.notivault.ui.components.StatTile
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaViewModel @Inject constructor(val vault: MediaVault, private val settings: SettingsStore) : ViewModel() {
    val removedOnly = MutableStateFlow(false)
    val stats = vault.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaStats(0, 0))
    val folders = settings.settings.map { it.mediaTrees }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val items = removedOnly.flatMapLatest { r -> Pager(PagingConfig(pageSize = 60)) { vault.page(r) }.flow }.cachedIn(viewModelScope)
    val scanning = MutableStateFlow(false)

    fun scan() = viewModelScope.launch {
        scanning.value = true
        runCatching { vault.scanAll() }
        scanning.value = false
    }

    fun addFolder(uri: Uri, resolver: android.content.ContentResolver) = viewModelScope.launch {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.addMediaTree(uri.toString())
        scan()
    }
}

@Composable
fun MediaScreen(onOpen: (Long) -> Unit, vm: MediaViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val removedOnly by vm.removedOnly.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    var picking by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.addFolder(it, context.contentResolver) }
    }
    LaunchedEffect(Unit) { if (folders.isNotEmpty()) vm.scan() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.md, end = Space.md, bottom = Space.xl),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = Space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Media", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() }.weight(1f))
                    if (folders.isNotEmpty()) {
                        IconButton(onClick = vm::scan, enabled = !scanning) { Icon(Icons.Outlined.Refresh, "Scan now") }
                        IconButton(onClick = { picking = true }) { Icon(Icons.Outlined.Add, "Add folder") }
                    }
                }
                Text("Photos, videos and voice notes kept even after the sender deletes them.",
                    style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary)
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        StatTile(Format.count(stats.total), "Kept", Modifier.weight(1f))
                        StatTile(Format.count(stats.removed), "Deleted by sender", Modifier.weight(1f), accent = Vault.colors.recovered)
                    }
                    FilterChipRow(listOf(ChipOption("all", "All"), ChipOption("removed", "Deleted by sender")),
                        if (removedOnly) "removed" else "all", { vm.removedOnly.value = it == "removed" },
                        Modifier.padding(top = Space.sm))
                }
            }
        }
        if (folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Outlined.PhotoLibrary, "Keep deleted photos & voice notes",
                    "Give NotiVault read access to WhatsApp's or Telegram's media folder. New media is copied into your encrypted vault, so it survives \"delete for everyone\".",
                    Modifier.height(420.dp)) {
                    FilledTonalButton(onClick = { picking = true }) {
                        Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(Space.xs)); Text("Choose a media folder")
                    }
                }
            }
        } else if (items.itemCount == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Outlined.PhotoLibrary, if (scanning) "Scanning…" else "Nothing here yet",
                    if (removedOnly) "Media the sender deletes will show up here." else "New photos and voice notes appear after the next scan.",
                    Modifier.height(320.dp))
            }
        } else {
            items(items.itemCount, key = items.itemKey { it.id }) { i -> items[i]?.let { MediaTile(it, vm.vault) { onOpen(it.id) } } }
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Which app's media?") },
            text = {
                Column {
                    Text("Android will open that folder; tap \"Use this folder\" to allow read-only access.",
                        style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    MediaRules.presets.forEach { p ->
                        Text(p.app, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()
                            .clickable { picking = false; picker.launch(MediaRules.initialUri(p)) }.padding(vertical = Space.sm))
                    }
                    Text("Another folder…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { picking = false; picker.launch(null) }.padding(vertical = Space.sm))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MediaTile(m: MediaEntity, vault: MediaVault, onClick: () -> Unit) {
    val type = MediaRules.type(m.displayName, m.mime)
    val thumb by produceState<android.graphics.Bitmap?>(null, m.id) { value = vault.thumbnail(m) }
    val removed = m.removedFromSourceAt != null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (removed) androidx.compose.foundation.BorderStroke(2.dp, Vault.colors.recovered) else null,
        modifier = Modifier.aspectRatio(1f).vaultSharedBounds("media-${m.id}", RoundedCornerShape(12.dp))
            .semantics { contentDescription = "${type?.name ?: "File"} ${m.displayName}" + if (removed) ", deleted by sender" else "" },
    ) {
        Box {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        when (type) { MediaType.Audio -> Icons.Outlined.AudioFile; MediaType.Document -> Icons.Outlined.Description; else -> Icons.Outlined.PhotoLibrary },
                        null, Modifier.size(32.dp), tint = Vault.colors.textSecondary,
                    )
                }
            if (type == MediaType.Video) Icon(Icons.Outlined.PlayCircle, null, Modifier.align(Alignment.Center).size(36.dp), tint = androidx.compose.ui.graphics.Color.White)
            if (removed) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Vault.colors.recoveredContainer).padding(4.dp)) {
                    Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(14.dp), tint = Vault.colors.recovered)
                }
            }
            if (type == MediaType.Audio || type == MediaType.Document) {
                Text(m.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
    }
}
