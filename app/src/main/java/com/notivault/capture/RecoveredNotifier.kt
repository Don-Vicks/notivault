package com.notivault.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.notivault.MainActivity
import com.notivault.R
import com.notivault.data.db.MessageRow

object RecoveredNotifier {
    const val CHANNEL_RECOVERED = "recovered"
    const val EXTRA_MESSAGE_ID = "message_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECOVERED, context.getString(R.string.channel_recovered), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun notify(context: Context, rows: List<MessageRow>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = NotificationManagerCompat.from(context)
        rows.forEach { row ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_MESSAGE_ID, row.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(context, row.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // Content is hidden on the lock screen; the full text lives behind the app lock.
            val n = NotificationCompat.Builder(context, CHANNEL_RECOVERED)
                .setSmallIcon(R.drawable.ic_stat_vault)
                .setColor(0xFFF5B544.toInt())
                .setContentTitle("Deleted message recovered")
                .setContentText("${row.sender} · ${row.appLabel}")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(row.id.toInt(), n)
        }
    }
}
