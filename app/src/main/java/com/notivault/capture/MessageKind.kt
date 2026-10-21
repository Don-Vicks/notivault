package com.notivault.capture

/** What a notification line represents. Media is captured as its label/preview only. */
enum class MessageKind(val label: String) {
    Text("Message"),
    Photo("Photo"),
    Video("Video"),
    Voice("Voice message"),
    Audio("Audio"),
    Sticker("Sticker"),
    Gif("GIF"),
    Document("Document"),
    Location("Location"),
    Contact("Contact"),
    Poll("Poll"),
    Call("Missed call"),
    Reaction("Reaction"),
    ;

    companion object {
        private val rules: List<Pair<MessageKind, Regex>> = listOf(
            Photo to """^(📷|🖼️?)\s*|^photo$|\bsent (you )?(a )?(photo|picture|image)|^image$""",
            Video to """^(🎥|📹)\s*|^video( \(\d+:\d+\))?$|\bsent (you )?a video""",
            Voice to """^(🎤|🎙️?)\s*|^voice (message|note)|^audio message|\bsent (you )?a voice""",
            Audio to """^(🎵|🎶)\s*|^audio( \(\d+:\d+\))?$""",
            Sticker to """^(sticker|💟|🩷)\b|sent (you )?a sticker|^\S+ sticker$""",
            Gif to """^(gif|👾 gif)\b|sent (you )?a gif""",
            Document to """^(📄|📎|📁)\s*|^(document|file)\b|sent (you )?(a )?(file|document|attachment)""",
            Location to """^(📍|🗺️?)\s*|^(live )?location\b|shared (a |their )?(live )?location""",
            Contact to """^(👤|📇)\s*|^contact( card)?\b|shared a contact""",
            Poll to """^(📊|🗳️?)\s*|^poll\b""",
            Call to """^missed (voice |video )?call|^(📞|☎️?)\s*missed""",
            Reaction to """^reacted\s|\breacted .+ to (your|a) |^liked (your|a) message|\bloved (your|a) message""",
        ).map { (k, p) -> k to Regex(p, RegexOption.IGNORE_CASE) }

        fun classify(text: String): MessageKind {
            val t = text.trim()
            return rules.firstOrNull { (_, r) -> r.containsMatchIn(t) }?.first ?: Text
        }
    }
}
