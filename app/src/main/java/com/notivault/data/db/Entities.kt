package com.notivault.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val captureEnabled: Boolean = true,
    val firstSeen: Long,
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["packageName", "key"], unique = true), Index("lastMessageAt")],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val key: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessageAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["hash"], unique = true),
        Index("conversationId"),
        Index("msgTimestamp"),
        Index("packageName"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val packageName: String,
    val sender: String,
    val text: String,
    val postedAt: Long,
    val msgTimestamp: Long,
    val hash: String,
    val deletedBySender: Boolean = false,
    val deletedDetectedAt: Long? = null,
    val attachmentPath: String? = null,
    @ColumnInfo(defaultValue = "Text") val kind: String = "Text",
    /** Weaker signal than [deletedBySender]: the app withdrew the notification on its own (see RemovalHeuristic). */
    val possiblyUnsentAt: Long? = null,
)

/** A media file copied (encrypted) from a chat app's media folder the user granted via SAF. */
@Entity(tableName = "media", indices = [Index(value = ["sourceUri"], unique = true), Index("capturedAt")])
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val treeUri: String,
    val displayName: String,
    val folder: String,
    val mime: String?,
    val size: Long,
    val sourceModified: Long,
    val capturedAt: Long,
    /** Encrypted copy; null when the file was too large to keep (metadata-only record). */
    val vaultPath: String?,
    /** Set when the original disappeared from the app's folder, e.g. "delete for everyone". */
    val removedFromSourceAt: Long? = null,
)

@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    val sender: String,
    val text: String,
)

/** Message joined with its conversation and app, as shown in lists. */
data class MessageRow(
    val id: Long,
    val conversationId: Long,
    val packageName: String,
    val sender: String,
    val text: String,
    val postedAt: Long,
    val msgTimestamp: Long,
    val hash: String,
    val deletedBySender: Boolean,
    val deletedDetectedAt: Long?,
    val attachmentPath: String?,
    val kind: String,
    val possiblyUnsentAt: Long?,
    @ColumnInfo(name = "conversationTitle") val conversationTitle: String,
    @ColumnInfo(name = "isGroup") val isGroup: Boolean,
    @ColumnInfo(name = "appLabel") val appLabel: String,
)

data class ConversationRow(
    val id: Long,
    val packageName: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessageAt: Long,
    val appLabel: String,
    val lastText: String?,
    val lastSender: String?,
    val messageCount: Int,
    val deletedCount: Int,
)

data class AppRow(
    val packageName: String,
    val label: String,
    val captureEnabled: Boolean,
    val firstSeen: Long,
    val messageCount: Int,
    val deletedCount: Int,
)

data class VaultStats(val total: Int, val deleted: Int, val conversations: Int, val apps: Int)

data class MediaStats(val total: Int, val removed: Int)
