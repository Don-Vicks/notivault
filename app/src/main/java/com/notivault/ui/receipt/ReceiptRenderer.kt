package com.notivault.ui.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.notivault.R
import com.notivault.data.db.MessageRow
import com.notivault.ui.components.Format
import java.io.File

/** Draws the receipt with the platform Canvas so it can be exported as PNG or PDF without screenshots. */
object ReceiptRenderer {
    private const val W = 1080
    private const val PAD = 72f
    private const val PAPER = 0xFFFFFDF8.toInt()
    private const val INK = 0xFF0B0D12.toInt()
    private const val MUTED = 0xFF6B7080.toInt()
    private const val AMBER = 0xFFB87400.toInt()
    private const val PAGE_BG = 0xFFEDEEF3.toInt()

    enum class Kind(val ext: String, val mime: String) { Png("png", "image/png"), Pdf("pdf", "application/pdf") }

    private lateinit var mono: TextPaint
    private lateinit var monoMuted: TextPaint
    private lateinit var monoBold: TextPaint
    private lateinit var body: TextPaint

    /** Same faces as the app: JetBrains Mono for metadata, Inter for the message body. */
    private fun loadPaints(context: Context) {
        if (::mono.isInitialized) return
        val monoFace = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        val interFace = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF
        val monoBoldFace = if (Build.VERSION.SDK_INT >= 28) Typeface.create(monoFace, 700, false) else Typeface.create(monoFace, Typeface.BOLD)
        mono = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = monoFace; textSize = 30f; color = INK }
        monoMuted = TextPaint(mono).apply { color = MUTED }
        monoBold = TextPaint(mono).apply { typeface = monoBoldFace; textSize = 34f; letterSpacing = 0.08f }
        body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = interFace; textSize = 40f; color = INK }
    }

    private fun layout(text: String, paint: TextPaint, width: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f).build()

    /** Lays out and draws onto [canvas]; returns the total height used. Call with null to measure. */
    private fun draw(canvas: Canvas?, row: MessageRow, thumb: Bitmap?): Int {
        val inner = (W - PAD * 2).toInt()
        val fields = buildList {
            add("APP" to row.appLabel)
            add("CHAT" to row.conversationTitle + if (row.isGroup) " (group)" else "")
            add("FROM" to row.sender)
            add("SENT" to Format.precise(row.msgTimestamp))
            add("CAPTURED" to Format.precise(row.postedAt))
            row.deletedDetectedAt?.takeIf { row.deletedBySender }?.let { add("DELETED" to Format.precise(it)) }
        }
        val labelW = 220
        val bodyLayout = layout(row.text, body, inner)
        val hashLayout = layout(row.hash.chunked(8).joinToString(" "), mono, inner)

        var y = PAD + 24f
        val c = canvas
        c?.drawColor(PAGE_BG)
        fun text(l: StaticLayout, x: Float) { c?.save(); c?.translate(x, y); l.draw(c ?: return); c.restore() }

        val start = y
        y += 50f
        val sub = layout("Captured on-device from a notification", monoMuted, inner)
        y += sub.height + 30f
        y += 30f // dashes
        fields.forEach { (_, v) -> y += maxOf(layout(v, mono, inner - labelW).height, 40) + 6 }
        val tz = layout("Times in ${Format.zone(row.msgTimestamp)}", monoMuted, inner)
        y += tz.height + 10f
        y += 60f
        y += bodyLayout.height + 30f
        if (thumb != null) y += (thumb.height * (inner.toFloat() / thumb.width)).coerceAtMost(900f) + 30f
        y += 60f + 40f + hashLayout.height + 60f
        val total = (y + PAD).toInt()
        if (c == null) return total

        // Pass 2: actually draw.
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        val rect = RectF(PAD / 2, PAD / 2, W - PAD / 2, total - PAD / 2)
        c.drawRoundRect(rect, 16f, 16f, paper)
        val holes = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAGE_BG }
        var hx = rect.left + 24f
        while (hx < rect.right) { c.drawCircle(hx, rect.top, 12f, holes); c.drawCircle(hx, rect.bottom, 12f, holes); hx += 44f }
        val dash = Paint().apply { color = MUTED; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f) }

        y = start
        text(layout("NOTIVAULT · MESSAGE RECEIPT", monoBold, inner), PAD); y += 50f
        text(sub, PAD); y += sub.height + 30f
        c.drawLine(PAD, y, W - PAD, y, dash); y += 30f
        fields.forEach { (k, v) ->
            val vl = layout(v, if (k == "DELETED") TextPaint(mono).apply { color = AMBER } else mono, inner - labelW)
            text(layout(k, monoMuted, labelW), PAD)
            text(vl, PAD + labelW)
            y += maxOf(vl.height, 40) + 6
        }
        y += 4f; text(tz, PAD); y += tz.height + 6f
        y += 20f; c.drawLine(PAD, y, W - PAD, y, dash); y += 40f
        text(bodyLayout, PAD); y += bodyLayout.height + 30f
        if (thumb != null) {
            val h = (thumb.height * (inner.toFloat() / thumb.width)).coerceAtMost(900f)
            val w = thumb.width * (h / thumb.height)
            c.drawBitmap(thumb, null, RectF(PAD, y, PAD + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG)); y += h + 30f
        }
        c.drawLine(PAD, y, W - PAD, y, dash); y += 60f
        text(layout("SHA-256", monoMuted, inner), PAD); y += 40f
        text(hashLayout, PAD); y += hashLayout.height + 20f
        text(layout("ID #${row.id.toString().padStart(8, '0')}", monoMuted, inner), PAD)

        if (row.deletedBySender) {
            val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AMBER; style = Paint.Style.STROKE; strokeWidth = 5f; pathEffect = DashPathEffect(floatArrayOf(22f, 8f), 0f)
            }
            val st = TextPaint(monoBold).apply { color = AMBER; textSize = 44f; letterSpacing = 0.2f }
            c.save(); c.rotate(-12f, W - 260f, start + 150f)
            val tw = st.measureText("RECOVERED")
            c.drawRoundRect(RectF(W - 260f - tw / 2 - 24, start + 110f, W - 260f + tw / 2 + 24, start + 180f), 10f, 10f, stamp)
            c.drawText("RECOVERED", W - 260f - tw / 2, start + 162f, st)
            c.restore()
        }
        return total
    }

    fun export(context: Context, row: MessageRow, thumbBytes: ByteArray?, kind: Kind): File {
        loadPaints(context)
        val thumb = thumbBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val h = draw(null, row, thumb)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "receipt-${row.id}.${kind.ext}")
        when (kind) {
            Kind.Png -> {
                val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
                draw(Canvas(bmp), row, thumb)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Kind.Pdf -> {
                val doc = PdfDocument()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W, h, 1).create())
                draw(page.canvas, row, thumb)
                doc.finishPage(page)
                file.outputStream().use { doc.writeTo(it) }
                doc.close()
            }
        }
        return file
    }

    fun share(context: Context, file: File, kind: Kind) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(kind.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
