package com.nclientnext.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Smooth heavy blur that works on every Android version (Modifier.blur needs Android 12+).
 * Shrinks the image to ~48 px wide, runs a 3-pass box blur (≈ gaussian), then scales back up.
 * [darken] adds a black veil so text on top stays readable.
 */
class BlurTransformation(private val darken: Float = 0f) : Transformation() {
    override val cacheKey: String = "blur2-$darken"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val src = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val w = 48
        val h = (src.height * w / src.width.coerceAtLeast(1)).coerceAtLeast(2)
        val small = src.scale(w, h, filter = true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 3) }
        small.setPixels(px, 0, w, 0, 0, w, h)
        val outW = 360
        val out = small.scale(outW, (h * outW / w).coerceAtLeast(2), filter = true).copy(Bitmap.Config.ARGB_8888, true)
        if (darken > 0f) Canvas(out).drawColor(android.graphics.Color.argb((darken * 255).toInt(), 0, 0, 0))
        return out
    }

    /** Separable box blur, horizontal then vertical, edges clamped. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, idx: (line: Int, i: Int) -> Int) {
            val div = 2 * r + 1
            for (line in 0 until lines) {
                var a = 0; var rr = 0; var g = 0; var b = 0
                for (k in -r..r) {
                    val c = src[idx(line, k.coerceIn(0, len - 1))]
                    a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                }
                for (i in 0 until len) {
                    dst[idx(line, i)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                    val out = src[idx(line, (i - r).coerceIn(0, len - 1))]
                    val inn = src[idx(line, (i + r + 1).coerceIn(0, len - 1))]
                    a += (inn ushr 24) - (out ushr 24)
                    rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                    g += ((inn shr 8) and 255) - ((out shr 8) and 255)
                    b += (inn and 255) - (out and 255)
                }
            }
        }
        pass(px, tmp, w, h) { line, i -> line * w + i }
        pass(tmp, px, h, w) { line, i -> i * w + line }
    }
}
