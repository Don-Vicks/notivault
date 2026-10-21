package com.notivault.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notivault.data.repo.VaultRepository
import com.notivault.ui.components.EmptyState
import com.notivault.ui.components.MessageCard
import com.notivault.ui.components.SearchField
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(repo: VaultRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val results = query.debounce(180).flatMapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun SearchScreen(onOpenMessage: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Text("Search", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() }.padding(start = Space.md, top = Space.lg, bottom = Space.sm))
        SearchField(q, { vm.query.value = it }, Modifier.padding(horizontal = Space.md))
        when {
            q.isBlank() -> EmptyState(Icons.Outlined.ManageSearch, "Find anything",
                "Search the text and senders of every message you've saved, including deleted ones.")
            results.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No matches", "Try fewer or different words.")
            else -> LazyColumn(contentPadding = PaddingValues(Space.md)) {
                item {
                    Text("${results.size}${if (results.size >= 300) "+" else ""} ${if (results.size == 1) "result" else "results"}",
                        style = MaterialTheme.typography.labelMedium, color = Vault.colors.textSecondary,
                        modifier = Modifier.padding(bottom = Space.xs))
                }
                items(results, key = { it.id }) { row ->
                    MessageCard(row, onClick = { onOpenMessage(row.id) }, highlight = q, modifier = Modifier.padding(vertical = Space.xxs))
                }
            }
        }
    }
}
