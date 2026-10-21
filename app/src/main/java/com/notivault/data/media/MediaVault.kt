package com.notivault.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import androidx.room.withTransaction
import com.notivault.data.crypto.KeyManager
import com.notivault.data.db.AppDatabase
import com.notivault.data.db.MediaEntity
import com.notivault.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScanResult(val added: Int, val removed: Int, val folders: Int)

/**
 * Keeps encrypted copies of the photos, videos and voice notes that chat apps download into their media
 * folders. When the sender deletes for everyone, the app removes its file. Our copy remains and the
 * record is flagged. Access is limited to folders the user explicitly granted through the Storage Access
 * Framework; no broad storage permission is used.
 */
class MediaVault(
    private val context: Context,
    private val db: AppDatabase,
    private val keys: KeyManager,
    private val settings: SettingsStore,
) {
    private val dao = db.mediaDao()
    private val thumbs = LruCache<Long, Bitmap>(80)

    val stats = dao.stats()
    fun page(removedOnly: Boolean) = dao.page(removedOnly)

    suspend fun scanAll(): ScanResult = withContext(Dispatchers.IO) {
        var added = 0; var removed = 0; var folders = 0
        for (tree in settings.current().mediaTrees) {
            val uri = Uri.parse(tree)
            if (!hasPermission(uri)) { settings.removeMediaTree(tree); continue }
            runCatching { scan(uri) }
                .onSuccess { (a, r) -> added += a; removed += r; folders++ }
                .onFailure { Log.w(TAG, "scan failed for $tree", it) }
        }
        ScanResult(added, removed, folders)
    }

    private fun hasPermission(tree: Uri) =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }

    private suspend fun scan(tree: Uri): Pair<Int, Int> {
        val files = mutableListOf<SourceFile>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), "", 0, files)
        val known = dao.knownUris(tree.toString()).toHashSet()
        var added = 0
        for (f in files) {
            if (f.uri.toString() in known) continue
            val vaultPath = if (f.size in 1..MAX_COPY_BYTES) copyEncrypted(f) else null
            val id = dao.insert(
                MediaEntity(
                    sourceUri = f.uri.toString(), treeUri = tree.toString(), displayName = f.name, folder = f.folder,
                    mime = f.mime, size = f.size, sourceModified = f.modified, capturedAt = System.currentTimeMillis(),
                    vaultPath = vaultPath,
                ),
            )
            if (id > 0) added++
        }
        // Only a complete walk can tell us what disappeared.
        val present = files.mapTo(HashSet()) { it.uri.toString() }
        val gone = dao.liveUris(tree.toString()).filter { it !in present }
        val removed = if (gone.isEmpty()) 0 else db.withTransaction { gone.chunked(500).sumOf { dao.markRemoved(it, System.currentTimeMillis()) } }
        return added to removed
    }

    private data class SourceFile(val uri: Uri, val name: String, val folder: String, val mime: String?, val size: Long, val modified: Long)

    private fun walk(tree: Uri, docId: String, folder: String, depth: Int, out: MutableList<SourceFile>) {
        if (depth > MAX_DEPTH) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2)
                if (MediaRules.skip(name)) continue
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(tree, id, if (folder.isEmpty()) name else "$folder/$name", depth + 1, out)
                } else if (MediaRules.isMedia(name, mime)) {
                    out += SourceFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, folder, mime, c.getLong(3), c.getLong(4))
                }
            }
        } ?: error("Folder not readable: $tree")
    }

    private fun copyEncrypted(f: SourceFile): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(f.uri)!!.use { it.readBytes() }
        val out = File(context.filesDir, "media/${java.util.UUID.randomUUID()}.bin")
        keys.writeEncrypted(out, bytes)
        out.absolutePath
    }.onFailure { Log.w(TAG, "copy failed: ${f.name}", it) }.getOrNull()

    suspend fun get(id: Long) = dao.get(id)

    fun decrypt(m: MediaEntity): ByteArray? = m.vaultPath?.let { keys.readEncrypted(File(it)) }

    /** Plaintext copy in the app cache for playback/sharing; callers delete it when done. */
    fun decryptToCache(m: MediaEntity): File? {
        val bytes = decrypt(m) ?: return null
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, m.displayName).apply { writeBytes(bytes) }
    }

    suspend fun thumbnail(m: MediaEntity, maxPx: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        thumbs.get(m.id)?.let { return@withContext it }
        val bytes = decrypt(m) ?: return@withContext null
        val bmp = when (MediaRules.type(m.displayName, m.mime)) {
            MediaType.Image -> decodeSampled(bytes, maxPx)
            MediaType.Video -> runCatching {
                val tmp = File.createTempFile("thumb", ".mp4", context.cacheDir).apply { writeBytes(bytes) }
                try {
                    MediaMetadataRetriever().run {
                        setDataSource(tmp.absolutePath)
                        getFrameAtTime(0)?.let { Bitmap.createScaledBitmap(it, maxPx, maxPx * it.height / it.width, true) }
                            .also { release() }
                    }
                } finally { tmp.delete() }
            }.getOrNull()
            else -> null
        }
        bmp?.also { thumbs.put(m.id, it) }
    }

    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun purgeOlderThan(cutoff: Long) {
        dao.pathsOlderThan(cutoff).forEach { File(it).delete() }
        dao.deleteOlderThan(cutoff)
    }

    suspend fun wipe() {
        dao.allPaths().forEach { File(it).delete() }
        dao.wipe()
        thumbs.evictAll()
    }

    companion object {
        private const val TAG = "MediaVault"
        const val MAX_COPY_BYTES = 40L * 1024 * 1024
        private const val MAX_DEPTH = 5
    }
}
