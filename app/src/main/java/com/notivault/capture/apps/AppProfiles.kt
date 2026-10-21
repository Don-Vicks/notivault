package com.notivault.capture.apps

/** How an app reveals that a sender deleted ("unsent") a message. */
enum class DeletionSignal {
    /** The message is replaced in place by text such as "This message was deleted". */
    Placeholder,
    /** The message silently disappears from the re-posted MessagingStyle history. */
    HistoryRemoval,
}

/** How non-MessagingStyle notifications encode group + sender. */
enum class TitleFormat {
    Plain,
    /** "Ada @ Squad" (WhatsApp legacy). */
    SenderAtGroup,
    /** "Squad: Ada" (WhatsApp/Telegram group titles). */
    GroupColonSender,
}

enum class AppCategory { Messaging, Social, Work }

data class AppProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val category: AppCategory,
    val deletion: Set<DeletionSignal> = emptySet(),
    val titleFormats: List<TitleFormat> = listOf(TitleFormat.Plain),
    /** Group or app-titled notifications put the sender in the body: "Ada: hello". */
    val textSenderPrefix: Boolean = false,
    val noise: List<Regex> = emptyList(),
)

private fun rx(vararg p: String) = p.map { Regex(it, RegexOption.IGNORE_CASE) }

object AppProfiles {

    val all: List<AppProfile> = listOf(
        AppProfile(
            "whatsapp", "WhatsApp", setOf("com.whatsapp", "com.whatsapp.w4b"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder),
            titleFormats = listOf(TitleFormat.SenderAtGroup, TitleFormat.GroupColonSender),
            textSenderPrefix = true,
            noise = rx("""^\d+ messages? from \d+ chats?$""", "^whatsapp web is currently active", "^backup paused", "^sending (message|media)"),
        ),
        AppProfile(
            "telegram", "Telegram",
            setOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
                "org.thunderdog.challegram", "org.telegram.plus", "tw.nekomimi.nekogram", "xyz.nextalone.nagram"),
            AppCategory.Messaging,
            deletion = setOf(DeletionSignal.HistoryRemoval),
            titleFormats = listOf(TitleFormat.GroupColonSender),
            textSenderPrefix = true,
            noise = rx("^updating", "^connecting", "^waiting for network", """^you have \d+ new messages?$"""),
        ),
        AppProfile(
            "signal", "Signal", setOf("org.thoughtcrime.securesms", "im.molly.app"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval),
            noise = rx("^new message$", "^signal is running", "^background connection enabled"),
        ),
        AppProfile(
            "messenger", "Messenger", setOf("com.facebook.orca", "com.facebook.mlite"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval),
            textSenderPrefix = true,
            noise = rx("^chat heads? (are )?active", "^messenger is running"),
        ),
        AppProfile(
            "instagram", "Instagram", setOf("com.instagram.android", "com.instagram.lite"), AppCategory.Social,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval),
            textSenderPrefix = true,
            noise = rx("^uploading", "^posting", "^your (post|reel|story) (is|was) (shared|uploaded)"),
        ),
        AppProfile("threads", "Threads", setOf("com.instagram.barcelona"), AppCategory.Social, textSenderPrefix = true),
        AppProfile(
            "facebook", "Facebook", setOf("com.facebook.katana", "com.facebook.lite"), AppCategory.Social,
            noise = rx("^uploading", "^posting"),
        ),
        AppProfile("x", "X", setOf("com.twitter.android", "com.twitter.android.lite"), AppCategory.Social,
            deletion = setOf(DeletionSignal.HistoryRemoval), textSenderPrefix = true),
        AppProfile(
            "snapchat", "Snapchat", setOf("com.snapchat.android"), AppCategory.Social,
            deletion = setOf(DeletionSignal.HistoryRemoval),
            noise = rx("is typing", "^saving", "^sending"),
        ),
        AppProfile(
            "discord", "Discord", setOf("com.discord"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.HistoryRemoval),
            noise = rx("^connected to voice", "^voice connected", "^discord is running"),
        ),
        AppProfile("slack", "Slack", setOf("com.Slack"), AppCategory.Work,
            deletion = setOf(DeletionSignal.HistoryRemoval), textSenderPrefix = true),
        AppProfile("teams", "Teams", setOf("com.microsoft.teams"), AppCategory.Work,
            deletion = setOf(DeletionSignal.HistoryRemoval), textSenderPrefix = true),
        AppProfile(
            "gmessages", "Messages", setOf("com.google.android.apps.messaging"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.HistoryRemoval),
            noise = rx("^sending", "^not sent", "^chat features"),
        ),
        AppProfile("samsung_messages", "Samsung Messages", setOf("com.samsung.android.messaging"), AppCategory.Messaging),
        AppProfile("aosp_sms", "Messaging", setOf("com.android.mms", "com.android.messaging"), AppCategory.Messaging),
        AppProfile("viber", "Viber", setOf("com.viber.voip"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval), textSenderPrefix = true),
        AppProfile("line", "LINE", setOf("jp.naver.line.android"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder), textSenderPrefix = true),
        AppProfile("wechat", "WeChat", setOf("com.tencent.mm"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder), textSenderPrefix = true),
        AppProfile("skype", "Skype", setOf("com.skype.raider", "com.skype.m2"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder)),
        AppProfile("imo", "imo", setOf("com.imo.android.imoim", "com.imo.android.imoimbeta"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder)),
        AppProfile("kakao", "KakaoTalk", setOf("com.kakao.talk"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder)),
        AppProfile("linkedin", "LinkedIn", setOf("com.linkedin.android"), AppCategory.Social, textSenderPrefix = true),
        AppProfile("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), AppCategory.Social,
            textSenderPrefix = true, noise = rx("^uploading", "^posting", "^your video")),
        AppProfile("reddit", "Reddit", setOf("com.reddit.frontpage"), AppCategory.Social),
        AppProfile("google_chat", "Google Chat", setOf("com.google.android.apps.dynamite"), AppCategory.Work,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval)),
        AppProfile("element", "Element", setOf("im.vector.app"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.Placeholder, DeletionSignal.HistoryRemoval)),
        AppProfile("threema", "Threema", setOf("ch.threema.app", "ch.threema.app.libre"), AppCategory.Messaging,
            deletion = setOf(DeletionSignal.HistoryRemoval)),
    )

    private val byPackage: Map<String, AppProfile> = all.flatMap { p -> p.packages.map { it to p } }.toMap()

    fun forPackage(pkg: String): AppProfile? = byPackage[pkg]

    fun isSocial(pkg: String) = pkg in byPackage

    /** Noise shared by every chat app: typing indicators, sync banners, call-in-progress. */
    val commonNoise = rx(
        """^\d+ new messages?$""",
        """^\d+ messages? from \d+ chats?$""",
        "^checking for new messages$",
        "^(incoming|ongoing|calling|ringing)( voice| video)? call",
        "^(is )?typing(…|\\.\\.\\.)?$",
        " is typing(…|\\.\\.\\.)?$",
        " (is )?recording (audio|a voice message|video)",
        "^backup in progress",
        "^syncing",
        "^tap to (view|see) (more|messages)$",
    )
}
