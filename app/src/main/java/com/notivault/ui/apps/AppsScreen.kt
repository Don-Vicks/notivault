package com.notivault.ui.apps

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notivault.capture.apps.AppProfiles
import com.notivault.capture.apps.DeletionSignal
import com.notivault.data.db.AppRow
import com.notivault.ui.components.SectionHeader
import com.notivault.data.repo.VaultRepository
import com.notivault.ui.components.AppAvatar
import com.notivault.ui.components.EmptyState
import com.notivault.ui.components.Format
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(private val repo: VaultRepository) : ViewModel() {
    val apps = repo.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun toggle(pkg: String, on: Boolean) = viewModelScope.launch { repo.setCaptureEnabled(pkg, on) }
}

@Composable
fun AppsScreen(vm: AppsViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
        item {
            Column(Modifier.padding(start = Space.md, end = Space.md, top = Space.lg, bottom = Space.sm)) {
                Text("Apps", style = MaterialTheme.typography.displaySmall)
                Text("Choose which apps are recorded. Apps appear here after their first notification. Turn off banking or one-time-code apps if you'd rather not store them.",
                    style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary)
            }
        }
        if (apps.isEmpty()) item { EmptyState(Icons.Outlined.Apps, "No apps yet", "Once notifications arrive, their apps show up here.") }
        val (social, other) = apps.partition { AppProfiles.isSocial(it.packageName) }
        if (social.isNotEmpty()) {
            item { SectionHeader("Messaging & social") }
            items(social, key = { it.packageName }) { AppItem(it) { on -> vm.toggle(it.packageName, on) } }
        }
        if (other.isNotEmpty()) {
            item { SectionHeader("Other apps") }
            items(other, key = { it.packageName }) { AppItem(it) { on -> vm.toggle(it.packageName, on) } }
        }
        item {
            Text(
                "Tuned for " + AppProfiles.all.joinToString(", ") { it.name } + ".",
                style = MaterialTheme.typography.bodySmall, color = Vault.colors.textSecondary,
                modifier = Modifier.padding(Space.md),
            )
        }
    }
}

@Composable
private fun AppItem(a: AppRow, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onToggle(!a.captureEnabled) }
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(a.packageName, a.label, Modifier.alpha(if (a.captureEnabled) 1f else 0.45f))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.titleSmall)
            AppProfiles.forPackage(a.packageName)?.let { p ->
                Text(deletionCaption(p.deletion), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (!a.captureEnabled) "Not recording" else
                    "${Format.count(a.messageCount)} saved" + if (a.deletedCount > 0) " · ${a.deletedCount} recovered" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (a.deletedCount > 0 && a.captureEnabled) Vault.colors.recovered else Vault.colors.textSecondary,
            )
        }
        Switch(checked = a.captureEnabled, onCheckedChange = null)
    }
}

private fun deletionCaption(signals: Set<DeletionSignal>) = when {
    signals.isEmpty() -> "Messages captured"
    signals.size == 2 -> "Deletion detection · placeholder + history"
    DeletionSignal.Placeholder in signals -> "Deletion detection · placeholder"
    else -> "Deletion detection · history"
}
