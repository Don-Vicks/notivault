package com.notivault.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.notivault.data.db.VaultStats
import com.notivault.data.repo.VaultRepository
import com.notivault.ui.components.ChipOption
import com.notivault.ui.components.DayDivider
import com.notivault.ui.components.EmptyState
import com.notivault.ui.components.FilterChipRow
import com.notivault.ui.components.Format
import com.notivault.ui.components.HealthBanner
import com.notivault.ui.components.MessageCard
import com.notivault.ui.components.ShimmerPlaceholder
import com.notivault.ui.components.StatTile
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class Range(val label: String, val days: Int) { Any("Any time", 0), Today("Today", 1), Week("7 days", 7), Month("30 days", 30) }

data class TimelineFilter(val pkg: String? = null, val recoveredOnly: Boolean = false, val range: Range = Range.Any)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(repo: VaultRepository) : ViewModel() {
    val filter = MutableStateFlow(TimelineFilter())
    val stats = repo.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultStats(0, 0, 0, 0))
    val apps = repo.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = filter.flatMapLatest { f ->
        val from = if (f.range.days == 0) 0L else System.currentTimeMillis() - f.range.days * 86_400_000L
        repo.timeline(f.pkg, f.recoveredOnly, from)
    }.cachedIn(viewModelScope)

    fun setApp(pkg: String?) = filter.update { it.copy(pkg = pkg) }
    fun setRecovered(on: Boolean) = filter.update { it.copy(recoveredOnly = on) }
    fun setRange(r: Range) = filter.update { it.copy(range = r) }
}

@Composable
fun TimelineScreen(
    listenerOn: Boolean,
    onFixListener: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    vm: TimelineViewModel = hiltViewModel(),
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
        item {
            Column(Modifier.padding(start = Space.md, end = Space.md, top = Space.lg, bottom = Space.sm)) {
                Text("Vault", style = MaterialTheme.typography.displaySmall)
                Text("Every notification, on the record.", style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    StatTile(Format.count(stats.total), "Saved", Modifier.weight(1f))
                    StatTile(Format.count(stats.deleted), "Recovered", Modifier.weight(1f), accent = Vault.colors.recovered)
                    StatTile(Format.count(stats.conversations), "Chats", Modifier.weight(1f))
                }
            }
        }
        if (!listenerOn) item { HealthBanner(onFixListener, Modifier.padding(vertical = Space.xs)) }
        item {
            FilterChipRow(
                options = listOf(ChipOption(null, "All apps")) + apps.filter { it.messageCount > 0 }.map { ChipOption(it.packageName, it.label) },
                selected = filter.pkg, onSelect = vm::setApp,
                modifier = Modifier.padding(top = Space.xs),
            )
            FilterChipRow(
                options = Range.entries.map { ChipOption(it.name, it.label) },
                selected = filter.range.name,
                onSelect = { k -> k?.let { vm.setRange(Range.valueOf(it)) } },
            ) {
                FilterChip(
                    selected = filter.recoveredOnly,
                    onClick = { vm.setRecovered(!filter.recoveredOnly) },
                    label = { Text("Recovered") },
                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp), tint = Vault.colors.recovered) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Vault.colors.recoveredContainer,
                        selectedLabelColor = Vault.colors.onRecoveredContainer,
                    ),
                )
                VerticalDivider(Modifier.height(24.dp))
            }
        }

        when {
            items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> item { ShimmerPlaceholder() }
            items.itemCount == 0 -> item {
                EmptyState(
                    Icons.Outlined.Inbox,
                    if (filter.recoveredOnly) "Nothing recovered yet" else "Your vault is empty",
                    if (filter.recoveredOnly) "When someone deletes a message you already received, it shows up here."
                    else "New notifications will appear here as they arrive.",
                    Modifier.height(360.dp),
                )
            }
            else -> {
                items(items.itemCount, key = items.itemKey { it.id }) { i ->
                    val row = items[i] ?: return@items
                    val prev = if (i > 0) items.peek(i - 1) else null
                    Column(Modifier.padding(horizontal = Space.md)) {
                        if (prev == null || !Format.sameDayTs(prev.msgTimestamp, row.msgTimestamp)) DayDivider(row.msgTimestamp)
                        MessageCard(row, onClick = { onOpenMessage(row.id) }, modifier = Modifier.padding(vertical = Space.xxs))
                    }
                }
            }
        }
    }
}

