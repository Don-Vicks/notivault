package com.notivault.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private fun cal(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** "14:05", "Yesterday", "Mon", "12 Mar", "12 Mar 2024". */
    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val t = cal(ts); val n = cal(now)
        val yesterday = cal(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(t, n) -> time(ts)
            sameDay(t, yesterday) -> "Yesterday"
            now - ts < 6 * 86_400_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ts))
            t.get(Calendar.YEAR) == n.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    fun time(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    fun dayHeader(ts: Long, now: Long = System.currentTimeMillis()): String {
        val t = cal(ts); val n = cal(now)
        val yesterday = cal(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(t, n) -> "Today"
            sameDay(t, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    /** Receipt-grade timestamp, to the second. Pair with [zone]. */
    fun precise(ts: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))

    fun zone(ts: Long): String = SimpleDateFormat("zzzz (zzz)", Locale.US).format(Date(ts))

    fun sameDayTs(a: Long, b: Long) = sameDay(cal(a), cal(b))

    fun count(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
        n >= 10_000 -> "%.0fk".format(n / 1_000f)
        n >= 1_000 -> "%.1fk".format(n / 1_000f)
        else -> n.toString()
    }
}
