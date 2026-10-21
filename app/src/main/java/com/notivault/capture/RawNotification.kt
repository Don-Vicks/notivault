package com.notivault.capture

/**
 * Framework-free snapshot of a posted notification. [NotificationExtractor] builds it from a
 * StatusBarNotification so the parsing logic below stays testable on the JVM.
 */
data class RawNotification(
    val packageName: String,
    val key: String,
    val postTime: Long,
    val whenTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val messages: List<RawMessage>,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val category: String?,
    /** ApplicationInfo the framework attaches to every notification (label fallback without package visibility). */
    val appInfo: android.content.pm.ApplicationInfo? = null,
)

data class RawMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long,
)

data class ParsedMessage(
    val packageName: String,
    val conversationKey: String,
    val conversationTitle: String,
    val isGroup: Boolean,
    val sender: String,
    val text: String,
    val msgTimestamp: Long,
    val postedAt: Long,
    /** True when it came from a MessagingStyle history, which apps re-post on every new message. */
    val fromHistory: Boolean = false,
    val kind: MessageKind = MessageKind.Text,
) {
    val hash: String get() = DedupEngine.hash(this)
}
