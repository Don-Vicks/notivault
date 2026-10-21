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
