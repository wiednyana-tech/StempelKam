package com.wiednyana.stempelkam

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/** Menampilkan pratinjau watermark di atas kamera, digambar dengan renderer yang sama seperti foto akhir. */
class WatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var data: WatermarkData = WatermarkData()
    var style: Int = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        WatermarkRenderer.draw(canvas, width, height, data, style)
    }
}
