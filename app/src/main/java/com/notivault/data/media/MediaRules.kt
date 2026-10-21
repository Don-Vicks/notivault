package com.notivault.data.media

import android.net.Uri
import android.provider.DocumentsContract

enum class MediaType { Image, Video, Audio, Document }

/** Which files in a chat app's media tree are worth keeping. Pure, unit-tested. */
object MediaRules {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov")
    private val audioExt = setOf("opus", "ogg", "m4a", "mp3", "aac", "amr", "wav")
    private val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "apk", "vcf")

    /** Hidden folders (".Statuses", ".nomedia", ".trash"), the user's own sent media, backups and caches. */
    fun skip(name: String): Boolean =
        name.startsWith(".") || name.equals("Sent", true) || name.equals("Private", true) ||
            name.equals("Databases", true) || name.equals("Backups", true) || name.equals("cache", true)

    fun type(name: String, mime: String?): MediaType? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime?.startsWith("image/") == true || ext in imageExt -> MediaType.Image
            mime?.startsWith("video/") == true || ext in videoExt -> MediaType.Video
            mime?.startsWith("audio/") == true || ext in audioExt -> MediaType.Audio
            ext in docExt -> MediaType.Document
            else -> null
        }
    }

    fun isMedia(name: String, mime: String?) = type(name, mime) != null

    /** Folders to open the system picker at, so granting is a single tap on the right directory. */
    data class Preset(val app: String, val docId: String)

    val presets = listOf(
        Preset("WhatsApp", "primary:Android/media/com.whatsapp/WhatsApp/Media"),
        Preset("WhatsApp Business", "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
        Preset("Telegram", "primary:Android/media/org.telegram.messenger/Telegram"),
        Preset("Telegram (older)", "primary:Telegram"),
    )

    fun initialUri(p: Preset): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", p.docId)
}
