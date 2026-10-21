package com.notivault.capture

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/** Translates framework notification objects into [RawNotification]. */
object NotificationExtractor {

    fun extract(sbn: StatusBarNotification): RawNotification {
        val n = sbn.notification
        val extras = n.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)

        val messages = style?.messages.orEmpty().mapNotNull { m ->
            val text = m.text?.toString() ?: return@mapNotNull null
            RawMessage(sender = m.person?.name?.toString(), text = text, timestamp = m.timestamp)
        }.ifEmpty { legacyMessages(extras) }

        return RawNotification(
            packageName = sbn.packageName,
            key = sbn.key,
            postTime = sbn.postTime,
            whenTime = n.`when`,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroupConversation = style?.isGroupConversation
                ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            messages = messages,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = n.category,
            appInfo = runCatching {
                @Suppress("DEPRECATION") extras.getParcelable<android.content.pm.ApplicationInfo>("android.appInfo")
            }.getOrNull(),
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyMessages(extras: Bundle): List<RawMessage> {
        val arr: Array<Parcelable> = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return arr.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
            RawMessage(b.getCharSequence("sender")?.toString(), text, b.getLong("time"))
        }
    }

    /** Big picture (e.g. photo preview) as a small JPEG, if the notification carries one. */
    @Suppress("DEPRECATION")
    fun thumbnail(context: Context, sbn: StatusBarNotification): ByteArray? {
        val extras = sbn.notification.extras
        val bmp: Bitmap? = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
            ?: if (Build.VERSION.SDK_INT >= 31) {
                (extras.get(Notification.EXTRA_PICTURE_ICON) as? Icon)?.loadDrawable(context)?.toBitmap()
            } else null
        return bmp?.let { compress(it) } ?: messagingImage(context, sbn)
    }

    /** Some apps attach the image itself to a MessagingStyle message; readable only if they granted access. */
    private fun messagingImage(context: Context, sbn: StatusBarNotification): ByteArray? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification) ?: return null
        val msg = style.messages.lastOrNull { it.dataUri != null && it.dataMimeType?.startsWith("image/") == true } ?: return null
        return runCatching {
            context.contentResolver.openInputStream(msg.dataUri!!)?.use { android.graphics.BitmapFactory.decodeStream(it) }?.let { compress(it) }
        }.getOrNull()
    }

    private fun compress(src: Bitmap, maxSide: Int = 720): ByteArray {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}
