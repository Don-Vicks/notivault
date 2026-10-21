package com.notivault.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.notivault.data.db.ConversationRow
import com.notivault.data.repo.VaultRepository
import androidx.compose.foundation.layout.Box
import com.notivault.ui.components.AppAvatar
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.motion.vaultSharedElement
import com.notivault.ui.components.ChipOption
import com.notivault.ui.components.EmptyState
import com.notivault.ui.components.FilterChipRow
import com.notivault.ui.components.Format
import com.notivault.ui.components.RecoveredBadge
import com.notivault.ui.components.ShimmerPlaceholder
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationsViewModel @Inject constructor(repo: VaultRepository) : ViewModel() {
    val pkg = MutableStateFlow<String?>(null)
    val apps = repo.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = pkg.flatMapLatest { repo.conversations(it) }.cachedIn(viewModelScope)
}

@Composable
fun ConversationsScreen(onOpen: (Long) -> Unit, vm: ConversationsViewModel = hiltViewModel()) {
    val pkg by vm.pkg.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
        item {
            Text("Chats", style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = Space.md, top = Space.lg, bottom = Space.sm))
            FilterChipRow(
                listOf(ChipOption(null, "All apps")) + apps.filter { it.messageCount > 0 }.map { ChipOption(it.packageName, it.label) },
                pkg, { vm.pkg.value = it }, Modifier.padding(bottom = Space.xs),
            )
        }
        when {
            items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> item { ShimmerPlaceholder() }
            items.itemCount == 0 -> item {
                EmptyState(Icons.AutoMirrored.Outlined.Chat, "No chats yet", "Conversations are grouped here per app and per person or group.")
            }
            else -> items(items.itemCount, key = items.itemKey { it.id }) { i ->
                items[i]?.let { ConversationItem(it) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
private fun ConversationItem(c: ConversationRow, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(c.packageName, c.appLabel, Modifier.vaultSharedElement("conv-avatar-${c.id}"), size = 52.dp)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.vaultSharedBounds("conv-title-${c.id}"))
                    }
                    Text(Format.relative(c.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = Vault.colors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString { if (c.isGroup && c.lastSender != null) append(c.lastSender).append(": "); append(c.lastText.orEmpty()) },
                        style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (c.deletedCount > 0) { Spacer(Modifier.width(Space.xs)); RecoveredBadge(compact = true) }
                }
                Text("${c.appLabel} · ${Format.count(c.messageCount)} saved" +
                    if (c.deletedCount > 0) " · ${c.deletedCount} recovered" else "",
                    style = MaterialTheme.typography.labelSmall, color = Vault.colors.textSecondary)
            }
        }
        HorizontalDivider(Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }
}

