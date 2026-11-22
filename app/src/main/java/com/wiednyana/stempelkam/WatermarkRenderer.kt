package com.wiednyana.stempelkam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Menggambar watermark. Semua ukuran berbasis lebar 1080 px lalu diskalakan,
 * sehingga pratinjau dan foto hasil terlihat identik.
 */
object WatermarkRenderer {

    val STYLE_NAMES = arrayOf("Klasik", "Kartu", "Minimal")

    private val ID = Locale("id", "ID")
    private const val ACCENT = 0xFFFFC107.toInt()
    private const val DARK = 0xFF1B1B1B.toInt()

    fun draw(c: Canvas, w: Int, h: Int, d: WatermarkData, style: Int) {
        if (w <= 0 || h <= 0) return
        val s = w / 1080f
        when (style % STYLE_NAMES.size) {
            0 -> drawClassic(c, w, h, d, s)
            1 -> drawCard(c, w, h, d, s)
            else -> drawMinimal(c, w, h, d, s)
        }
    }

    // ---------- Gaya 1: Klasik (jam besar + garis aksen) ----------
    private fun drawClassic(c: Canvas, w: Int, h: Int, d: WatermarkData, s: Float) {
        scrim(c, w, h, 0.40f)
        val pad = 40f * s
        val info = paint(34f, s)
        val lines = infoLines(d, info, w - 2 * pad, withLabels = false)

        var y = h - pad
        for (i in lines.indices.reversed()) {
            c.drawText(lines[i], pad, y, info)
            y -= info.fontSpacing
        }

        val timeP = paint(124f, s, bold = true)
        val dateP = paint(38f, s, bold = true)
        val dayP = paint(36f, s)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }

        val base = y - 4f * s
        val timeStr = fmt("HH:mm", d)
        c.drawText(timeStr, pad, base, timeP)
        val capTop = base - timeP.textSize * 0.72f
        val barX = pad + timeP.measureText(timeStr) + 22f * s
        c.drawRect(barX, capTop, barX + 8f * s, base, accent)
        val tx = barX + 26f * s
        c.drawText(fmt("dd MMM yyyy", d), tx, capTop + dateP.textSize * 0.85f, dateP)
        c.drawText(fmt("EEEE", d), tx, base, dayP)

        if (d.showNote && d.note.isNotBlank()) {
            val noteP = paint(36f, s, bold = true, color = DARK, shadow = false)
            val text = ellipsize(d.note, noteP, w - 2 * pad - 40f * s)
            val bottom = capTop - 26f * s
            val lh = noteP.textSize + 26f * s
            val rect = RectF(pad, bottom - lh, pad + noteP.measureText(text) + 40f * s, bottom)
            c.drawRoundRect(rect, 10f * s, 10f * s, accent)
            c.drawText(text, pad + 20f * s, rect.centerY() - (noteP.ascent() + noteP.descent()) / 2f, noteP)
        }
    }

    // ---------- Gaya 2: Kartu laporan ----------
    private fun drawCard(c: Canvas, w: Int, h: Int, d: WatermarkData, s: Float) {
        val pad = 32f * s
        val inner = 28f * s
        val cardW = w - 2 * pad
        val labelP = paint(30f, s, color = 0xFFB0BEC5.toInt(), shadow = false)
        val valP = paint(32f, s, shadow = false)
        val labelW = 200f * s
        val valW = cardW - 2 * inner - labelW

        val rows = mutableListOf<Pair<String, List<String>>>()
        rows += "Waktu" to listOf(fmt("EEEE, dd MMM yyyy  HH:mm:ss", d))
        if (d.showAddress) rows += "Lokasi" to wrap(addressText(d), valP, valW)
        if (d.showCoords && d.hasLocation) rows += "Koordinat" to listOf(coordText(d))
        if (d.showAltitude && d.hasLocation && d.altitude != null) {
            rows += "Ketinggian" to listOf(String.format(Locale.US, "%.0f m dpl", d.altitude))
        }

        val lh = valP.fontSpacing
        val gap = 10f * s
        val headerH = 78f * s
        val bodyH = 2 * inner + rows.sumOf { it.second.size } * lh + (rows.size - 1) * gap
        val top = h - pad - headerH - bodyH
        val left = pad
        val right = pad + cardW
        val r = 18f * s

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC0101418.toInt() }
        c.drawRoundRect(RectF(left, top, right, h - pad), r, r, bg)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        c.drawRoundRect(RectF(left, top, right, top + headerH), r, r, accent)
        c.drawRect(left, top + headerH - r, right, top + headerH, accent)

        val titleP = paint(38f, s, bold = true, color = DARK, shadow = false)
        val title = if (d.showNote && d.note.isNotBlank()) d.note else "Dokumentasi Lapangan"
        val titleText = ellipsize(title, titleP, cardW - 2 * inner)
        c.drawText(titleText, left + inner, top + headerH / 2f - (titleP.ascent() + titleP.descent()) / 2f, titleP)

        var y = top + headerH + inner
        for ((label, values) in rows) {
            val baseline = y - valP.ascent()
            c.drawText(label, left + inner, baseline, labelP)
            values.forEachIndexed { i, v ->
                c.drawText(v, left + inner + labelW, baseline + i * lh, valP)
            }
            y += values.size * lh + gap
        }
    }

    // ---------- Gaya 3: Minimal rata kanan ----------
    private fun drawMinimal(c: Canvas, w: Int, h: Int, d: WatermarkData, s: Float) {
        scrim(c, w, h, 0.30f)
        val pad = 36f * s
        val x = w - pad
        val p = paint(32f, s).apply { textAlign = Paint.Align.RIGHT }
        val big = paint(46f, s, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val noteP = paint(36f, s, bold = true, color = ACCENT).apply { textAlign = Paint.Align.RIGHT }

        val lines = infoLines(d, p, w * 0.78f, withLabels = false)
        var y = h - pad
        for (i in lines.indices.reversed()) {
            c.drawText(lines[i], x, y, p)
            y -= p.fontSpacing
        }
        y -= 6f * s
        c.drawText(fmt("dd/MM/yyyy  HH:mm:ss", d), x, y, big)
        y -= big.fontSpacing
        if (d.showNote && d.note.isNotBlank()) {
            c.drawText(ellipsize(d.note, noteP, w * 0.8f), x, y, noteP)
        }
    }

    // ---------- Utilitas ----------
    private fun infoLines(d: WatermarkData, p: Paint, maxW: Float, withLabels: Boolean): List<String> {
        val out = mutableListOf<String>()
        if (d.showAddress) out += wrap(addressText(d), p, maxW)
        if (d.showCoords && d.hasLocation) out += (if (withLabels) "Koordinat: " else "") + coordText(d)
        if (d.showAltitude && d.hasLocation && d.altitude != null) {
            out += String.format(Locale.US, "Ketinggian: %.0f m", d.altitude)
        }
        return out
    }

    private fun addressText(d: WatermarkData): String = when {
        d.address.isNotBlank() -> d.address
        d.hasLocation -> "Mencari alamat…"
        else -> "Mencari lokasi GPS…"
    }

    private fun coordText(d: WatermarkData): String {
        val ns = if (d.lat < 0) "S" else "N"
        val ew = if (d.lon < 0) "W" else "E"
        return String.format(Locale.US, "%.6f°%s  %.6f°%s", Math.abs(d.lat), ns, Math.abs(d.lon), ew)
    }

    private fun fmt(pattern: String, d: WatermarkData): String =
        SimpleDateFormat(pattern, ID).format(d.time)

    private fun paint(
        size: Float,
        s: Float,
        bold: Boolean = false,
        color: Int = Color.WHITE,
        shadow: Boolean = true,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size * s
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (shadow) setShadowLayer(4f * s, 0f, 2f * s, 0xAA000000.toInt())
    }

    private fun scrim(c: Canvas, w: Int, h: Int, frac: Float) {
        val top = h * (1f - frac)
        val p = Paint().apply {
            shader = LinearGradient(0f, top, 0f, h.toFloat(), 0x00000000, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, top, w.toFloat(), h.toFloat(), p)
    }

    private fun ellipsize(text: String, p: TextPaint, maxW: Float): String =
        TextUtils.ellipsize(text, p, maxW, TextUtils.TruncateAt.END).toString()

    private fun wrap(text: String, p: Paint, maxW: Float, maxLines: Int = 3): List<String> {
        val res = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty() && res.size < maxLines) {
            var n = p.breakText(rest, true, maxW, null)
            if (n <= 0) break
            if (n < rest.length) {
                val sp = rest.lastIndexOf(' ', n)
                if (sp > 0) n = sp
            }
            res += rest.substring(0, n).trim()
            rest = rest.substring(n).trim()
        }
        if (rest.isNotEmpty() && res.isNotEmpty()) {
            res[res.size - 1] = res.last().trimEnd(',', ' ') + "…"
        }
        return res
    }
}
