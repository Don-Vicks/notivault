package com.notivault.capture

import com.notivault.capture.apps.AppProfile
import com.notivault.capture.apps.AppProfiles
import com.notivault.capture.apps.TitleFormat

object NotificationParser {

    private const val SELF_SENDER = "You"

    /** Returns the individual messages contained in a notification, or empty if it should be ignored. */
    fun parse(raw: RawNotification, ownPackage: String, appLabel: String? = null): List<ParsedMessage> {
        if (shouldIgnore(raw, ownPackage)) return emptyList()
        val profile = AppProfiles.forPackage(raw.packageName)
        val appName = profile?.name ?: appLabel

        if (raw.messages.isNotEmpty()) {
            val conversationTitle = TitleCleaner.clean(raw.conversationTitle) ?: TitleCleaner.clean(raw.title) ?: appName ?: raw.packageName
            return raw.messages.mapNotNull { m ->
                val text = clean(m.text) ?: return@mapNotNull null
                if (NoiseFilter.isNoise(text, profile)) return@mapNotNull null
                val sender = clean(m.sender) ?: SELF_SENDER
                build(raw, conversationTitle, raw.isGroupConversation, sender, text, m.timestamp, fromHistory = true)
            }
        }

        val body = clean(raw.bigText) ?: clean(raw.text) ?: return emptyList()
        if (NoiseFilter.isNoise(body, profile)) return emptyList()
        val title = TitleCleaner.clean(raw.title)
        val titled = splitTitle(title, profile)
        var sender = titled?.first ?: title ?: appName ?: raw.packageName
        var conversation = titled?.second ?: TitleCleaner.clean(raw.conversationTitle) ?: title ?: appName ?: raw.packageName
        var isGroup = raw.isGroupConversation || titled != null
        var text = body

        // "Ada: hello" in the body, under a group title or a bare app-name title.
        if (profile?.textSenderPrefix == true && titled == null) {
            SENDER_PREFIX.matchEntire(body)?.let { mr ->
                val prefix = mr.groupValues[1].trim()
                val titleIsApp = title == null || title.equals(appName, ignoreCase = true)
                if (titleIsApp) {
                    sender = prefix; conversation = prefix
                } else if (raw.isGroupConversation || !prefix.equals(title, ignoreCase = true)) {
                    sender = prefix; isGroup = true
                }
                text = mr.groupValues[2].trim()
            }
        }
        if (NoiseFilter.isNoise(text, profile)) return emptyList()
        val ts = if (raw.whenTime > 0) raw.whenTime else raw.postTime
        return listOf(build(raw, conversation, isGroup, sender, text, ts, fromHistory = false))
    }

    private fun build(raw: RawNotification, conversation: String, group: Boolean, sender: String, text: String, ts: Long, fromHistory: Boolean) =
        ParsedMessage(
            packageName = raw.packageName,
            conversationKey = conversation.lowercase(),
            conversationTitle = conversation,
            isGroup = group,
            sender = sender,
            text = text,
            msgTimestamp = ts,
            postedAt = raw.postTime,
            fromHistory = fromHistory,
            kind = MessageKind.classify(text),
        )

    /** Returns (sender, group) when the title encodes both. */
    internal fun splitTitle(title: String?, profile: AppProfile?): Pair<String, String>? {
        if (title == null || profile == null) return null
        for (f in profile.titleFormats) when (f) {
            TitleFormat.SenderAtGroup -> AT_GROUP.matchEntire(title)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() }
            TitleFormat.GroupColonSender -> COLON.matchEntire(title)?.let { return it.groupValues[2].trim() to it.groupValues[1].trim() }
            TitleFormat.Plain -> Unit
        }
        return null
    }

    private fun shouldIgnore(raw: RawNotification, ownPackage: String): Boolean =
        raw.packageName == ownPackage ||
            raw.packageName in SYSTEM_PACKAGES ||
            raw.isOngoing ||
            raw.isGroupSummary ||
            raw.category in IGNORED_CATEGORIES

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    private val AT_GROUP = Regex("""^(.{1,60}?) @ (.{1,80})$""")
    private val COLON = Regex("""^(.{1,80}?): (.{1,60})$""")
    private val SENDER_PREFIX = Regex("""^([^:\n]{1,40}):\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)
    private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui", "com.android.providers.downloads")
    private val IGNORED_CATEGORIES = setOf("progress", "service", "transport", "sys", "navigation", "stopwatch")
}

/** Strips unread counters that apps append to titles: "Squad (5 messages)", "Squad · 3 new". */
object TitleCleaner {
    private val counters = listOf(
        Regex("""\s*\(\d+ (new )?messages?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*[·•]\s*\d+ (new )?(messages?)?$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(\d+\)$"""),
    )

    fun clean(s: String?): String? {
        var t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        counters.forEach { t = t.replace(it, "") }
        return t.trim().takeIf { it.isNotEmpty() }
    }
}

/** Placeholder/summary text that messaging apps post which carries no message content. */
object NoiseFilter {
    fun isNoise(text: String, profile: AppProfile? = null): Boolean {
        val t = text.trim()
        return AppProfiles.commonNoise.any { it.containsMatchIn(t) } || profile?.noise?.any { it.containsMatchIn(t) } == true
    }
}
