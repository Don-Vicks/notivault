package com.notivault.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.notivault.data.repo.VaultRepository
import com.notivault.ui.components.AppAvatar
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.motion.vaultSharedElement
import com.notivault.ui.components.ChatBubble
import com.notivault.ui.components.DayDivider
import com.notivault.ui.components.Format
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.material.icons.outlined.IosShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(handle: SavedStateHandle, private val repo: VaultRepository) : ViewModel() {
    private val id: Long = checkNotNull(handle["id"])

    fun export(context: android.content.Context) = viewModelScope.launch {
        val (title, text) = repo.transcript(id) ?: return@launch
        val file = withContext(Dispatchers.IO) {
            java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                .let { java.io.File(it, title.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".txt") }.apply { writeText(text) }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export chat"))
    }
    val header = repo.conversationHeader(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val items = repo.conversationMessages(id).cachedIn(viewModelScope)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(onBack: () -> Unit, onOpenMessage: (Long) -> Unit, vm: ConversationViewModel = hiltViewModel()) {
    val header by vm.header.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                title = {
                    header?.let { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppAvatar(h.packageName, h.appLabel, Modifier.vaultSharedElement("conv-avatar-${h.id}"), size = 36.dp)
                            Spacer(Modifier.width(Space.sm))
                            Column {
                                Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.vaultSharedBounds("conv-title-${h.id}"))
                                Text(h.appLabel + if (h.isGroup) " · group" else "", style = MaterialTheme.typography.labelSmall,
                                    color = Vault.colors.textSecondary)
                            }
                        }
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = { vm.export(context) }) { Icon(androidx.compose.material.icons.Icons.Outlined.IosShare, "Export chat as text") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        // Newest at the bottom, like a chat: data is DESC and the list is reversed.
        LazyColumn(Modifier.fillMaxSize().padding(pad), reverseLayout = true,
            contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm)) {
            items(items.itemCount, key = items.itemKey { it.id }) { i ->
                val row = items[i] ?: return@items
                val older = if (i + 1 < items.itemCount) items.peek(i + 1) else null
                // Label senders in any chat where the sender isn't simply the chat itself (groups, or apps that omit the group flag).
                val multiParty = row.isGroup || row.sender != row.conversationTitle
                val showSender = multiParty && older?.sender != row.sender
                Column(Modifier.padding(vertical = 2.dp)) {
                    if (older == null || !Format.sameDayTs(older.msgTimestamp, row.msgTimestamp)) DayDivider(row.msgTimestamp)
                    ChatBubble(row, showSender = showSender || (multiParty && older == null), onClick = { onOpenMessage(row.id) })
                }
            }
        }
    }
}
