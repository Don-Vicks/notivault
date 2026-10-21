package com.notivault.ui.media

import android.content.Intent
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.notivault.data.db.MediaEntity
import com.notivault.data.media.MediaRules
import com.notivault.data.media.MediaType
import com.notivault.data.media.MediaVault
import com.notivault.ui.components.Format
import com.notivault.ui.components.RecoveredBadge
import com.notivault.ui.motion.vaultSharedBounds
import com.notivault.ui.theme.MonoStyle
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(handle: SavedStateHandle, val vault: MediaVault) : ViewModel() {
    val id: Long = checkNotNull(handle["id"])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(onBack: () -> Unit, vm: MediaViewerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val media by produceState<MediaEntity?>(null, vm.id) { value = vm.vault.get(vm.id) }
    val m = media
    val type = m?.let { MediaRules.type(it.displayName, it.mime) }
    // Plaintext lives only in the app cache while this screen is open.
    val file by produceState<File?>(null, m) { value = m?.let { withContext(Dispatchers.IO) { vm.vault.decryptToCache(it) } } }
    DisposableEffect(file) { onDispose { file?.delete() } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(m?.displayName ?: "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                file?.let { f ->
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType(m?.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                    }) { Icon(Icons.Outlined.Share, "Share") }
                }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).vaultSharedBounds("media-${vm.id}", RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))) {
                val f = file
                when {
                    m?.vaultPath == null && m != null -> Text("This file was larger than 40 MB, so only its details were recorded.",
                        style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary, modifier = Modifier.padding(Space.md))
                    f == null -> Unit
                    type == MediaType.Image -> {
                        val bmp by produceState<android.graphics.Bitmap?>(null, f) {
                            value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }
                        }
                        bmp?.let { Image(it.asImageBitmap(), m?.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
                    }
                    type == MediaType.Video || type == MediaType.Audio -> AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(f.absolutePath)
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(if (type == MediaType.Audio) 120.dp else 320.dp),
                    )
                    else -> Text("Use Share to open this document.", modifier = Modifier.padding(Space.md))
                }
            }
            m?.let {
                if (it.removedFromSourceAt != null) RecoveredBadge()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Field("FOLDER", it.folder.ifEmpty { "/" })
                    Field("RECEIVED", Format.precise(it.sourceModified))
                    Field("KEPT", Format.precise(it.capturedAt))
                    it.removedFromSourceAt?.let { r -> Field("DELETED", Format.precise(r)) }
                    Field("SIZE", "%.1f MB".format(it.size / 1_048_576f))
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    androidx.compose.foundation.layout.Row {
        Text(label, style = MonoStyle, color = Vault.colors.textSecondary, modifier = Modifier.padding(end = Space.md).fillMaxWidth(0.3f))
        Text(value, style = MonoStyle)
    }
}
