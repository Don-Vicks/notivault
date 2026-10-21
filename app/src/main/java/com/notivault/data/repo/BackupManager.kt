package com.notivault.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.notivault.data.crypto.KeyManager
import com.notivault.data.crypto.PasswordCrypto
import com.notivault.data.db.AppDatabase
import com.notivault.data.db.AppEntity
import com.notivault.data.db.ConversationEntity
import com.notivault.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Portable, password-encrypted `.nvault` backups written through the Storage Access Framework. */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val keys: KeyManager,
) {
    private val dao = db.dao()

    suspend fun export(target: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val apps = dao.allApps()
        val convs = dao.allConversations()
        val msgs = dao.allMessages()
        val root = JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("apps", JSONArray(apps.map {
                JSONObject().put("pkg", it.packageName).put("label", it.label)
                    .put("enabled", it.captureEnabled).put("firstSeen", it.firstSeen)
            }))
            .put("conversations", JSONArray(convs.map {
                JSONObject().put("id", it.id).put("pkg", it.packageName).put("key", it.key)
                    .put("title", it.title).put("group", it.isGroup).put("last", it.lastMessageAt)
            }))
            .put("messages", JSONArray(msgs.map { m ->
                JSONObject().put("conv", m.conversationId).put("pkg", m.packageName).put("sender", m.sender)
                    .put("text", m.text).put("posted", m.postedAt).put("ts", m.msgTimestamp).put("hash", m.hash)
                    .put("deleted", m.deletedBySender).put("deletedAt", m.deletedDetectedAt ?: 0L).put("kind", m.kind)
                    .apply {
                        m.attachmentPath?.let { keys.readEncrypted(File(it)) }
                            ?.let { put("thumb", Base64.encodeToString(it, Base64.NO_WRAP)) }
                    }
            }))
        val sealed = PasswordCrypto.seal(gzip(root.toString().toByteArray()), password)
        context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(sealed) }
        msgs.size
    }

    /** Merges a backup into the vault; existing messages (same hash) are kept. Returns messages added. */
    suspend fun import(source: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val blob = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        val root = JSONObject(String(gunzip(PasswordCrypto.open(blob, password))))
        var added = 0
        db.withTransaction {
            root.getJSONArray("apps").forEachObject {
                dao.insertApp(AppEntity(it.getString("pkg"), it.getString("label"), it.getBoolean("enabled"), it.getLong("firstSeen")))
            }
            val idMap = HashMap<Long, Long>()
            root.getJSONArray("conversations").forEachObject {
                val pkg = it.getString("pkg"); val key = it.getString("key")
                val existing = dao.conversation(pkg, key)
                idMap[it.getLong("id")] = existing?.id ?: dao.insertConversation(
                    ConversationEntity(packageName = pkg, key = key, title = it.getString("title"),
                        isGroup = it.getBoolean("group"), lastMessageAt = it.getLong("last")),
                )
                existing?.let { e -> dao.touchConversation(e.id, it.getLong("last"), e.title, e.isGroup) }
            }
            root.getJSONArray("messages").forEachObject {
                val convId = idMap[it.getLong("conv")] ?: return@forEachObject
                val hash = it.getString("hash")
                val thumbPath = it.optString("thumb").takeIf { t -> t.isNotEmpty() }?.let { t ->
                    File(context.filesDir, "attachments/$hash.bin").also { f ->
                        keys.writeEncrypted(f, Base64.decode(t, Base64.NO_WRAP))
                    }.absolutePath
                }
                val id = dao.insertMessage(
                    MessageEntity(
                        conversationId = convId, packageName = it.getString("pkg"), sender = it.getString("sender"),
                        text = it.getString("text"), postedAt = it.getLong("posted"), msgTimestamp = it.getLong("ts"),
                        hash = hash, deletedBySender = it.getBoolean("deleted"),
                        deletedDetectedAt = it.getLong("deletedAt").takeIf { d -> d > 0 }, attachmentPath = thumbPath,
                        kind = it.optString("kind", "Text"),
                    ),
                )
                if (id > 0) added++
            }
        }
        added
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    companion object {
        fun gzip(bytes: ByteArray): ByteArray =
            ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

        fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }
}
