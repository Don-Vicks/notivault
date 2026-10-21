package com.notivault.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notivault.data.db.MessageRow
import com.notivault.data.repo.VaultRepository
import androidx.compose.foundation.shape.RoundedCornerShape
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.receipt.ReceiptCard
import com.notivault.ui.receipt.ReceiptRenderer
import com.notivault.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(handle: SavedStateHandle, private val repo: VaultRepository) : ViewModel() {
    val id: Long = checkNotNull(handle["id"])
    val message = repo.message(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun thumbnail(row: MessageRow): ByteArray? = row.attachmentPath?.let { repo.readAttachment(it) }

    fun export(context: Context, row: MessageRow, kind: ReceiptRenderer.Kind) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { ReceiptRenderer.export(context, row, thumbnail(row), kind) }
        ReceiptRenderer.share(context, file, kind)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(onBack: () -> Unit, onOpenConversation: (Long) -> Unit, vm: MessageViewModel = hiltViewModel()) {
    val row by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Receipt") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                row?.let { r ->
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("message", r.text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Outlined.ContentCopy, "Copy text") }
                }
            },
        )
    }) { pad ->
        val r = row
        val thumb = remember(r?.attachmentPath) {
            r?.let { vm.thumbnail(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(Space.md)) {
            // Present from the first frame so the tapped card has a target to morph into while the row loads.
            Box(
                Modifier.vaultSharedBounds("msg-${vm.id}", RoundedCornerShape(6.dp)).fillMaxWidth().heightIn(min = 420.dp)
                    .then(if (r == null) Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainer) else Modifier),
            ) {
                if (r != null) ReceiptCard(r)
            }
            if (r == null) return@Column
            if (thumb != null) {
                Spacer(Modifier.height(Space.md))
                Image(thumb, "Photo preview from the notification", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(MaterialTheme.shapes.medium))
            }
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilledTonalButton(onClick = { vm.export(context, r, ReceiptRenderer.Kind.Png) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.Image, "Share as image"); Spacer(Modifier.padding(4.dp)); Text("Image")
                }
                FilledTonalButton(onClick = { vm.export(context, r, ReceiptRenderer.Kind.Pdf) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, "Share as PDF"); Spacer(Modifier.padding(4.dp)); Text("PDF")
                }
            }
            Spacer(Modifier.height(Space.sm))
            OutlinedButton(onClick = { onOpenConversation(r.conversationId) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Chat, null); Spacer(Modifier.padding(4.dp)); Text("Open conversation")
            }
        }
    }
}
