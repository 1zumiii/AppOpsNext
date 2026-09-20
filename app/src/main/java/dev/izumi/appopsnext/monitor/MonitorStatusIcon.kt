package dev.izumi.appopsnext.monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.core.content.res.ResourcesCompat
import dev.izumi.appopsnext.R

/**
 * Draws the status-bar icon with the number of reported accesses on it.
 *
 * One icon that carries a count takes a single status-bar slot no matter how
 * many accesses there were, which is why the accesses are reported as one
 * grouped notification rather than one notification each.
 *
 * The system tints status-bar icons from their alpha channel, so everything is
 * drawn opaque white and the shape comes from what is left transparent.
 */
internal object MonitorStatusIcon {
    fun create(context: Context, count: Int): Icon {
        val size = ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val glyph = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.ic_notification_monitor,
            null,
        )
        // The glyph is centred in its own drawable; it is only moved aside when
        // there is a count to make room for.
        if (count > 0) {
            val glyphSize = (size * GLYPH_SCALE_WITH_COUNT).toInt()
            glyph?.setBounds(0, 0, glyphSize, glyphSize)
        } else {
            glyph?.setBounds(0, 0, size, size)
        }
        glyph?.draw(canvas)

        if (count > 0) {
            val label = if (count > MAX_COUNT) "$MAX_COUNT+" else count.toString()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = if (label.length > 1) size * 0.42f else size * 0.52f
                textAlign = Paint.Align.RIGHT
            }
            // Sits in the clear right third of the glyph, baseline aligned to
            // the bottom so a two-character label still fits the square.
            canvas.drawText(
                label,
                size * 0.99f,
                size * 0.98f,
                paint,
            )
        }
        return Icon.createWithBitmap(bitmap)
    }

    private const val GLYPH_SCALE_WITH_COUNT = 0.74f
    private const val ICON_SIZE_PX = 96
    private const val MAX_COUNT = 9
}
