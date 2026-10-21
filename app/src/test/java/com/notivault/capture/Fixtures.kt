package com.notivault.capture

fun raw(
    pkg: String = "com.whatsapp",
    title: String? = "Alice",
    text: String? = "hi",
    bigText: String? = null,
    conversationTitle: String? = null,
    group: Boolean = false,
    messages: List<RawMessage> = emptyList(),
    ongoing: Boolean = false,
    summary: Boolean = false,
    category: String? = "msg",
    postTime: Long = 1_000L,
    whenTime: Long = 900L,
) = RawNotification(pkg, "key", postTime, whenTime, title, text, bigText, null, conversationTitle, group, messages, ongoing, summary, category)
