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
        val label = when {
            count <= 0 -> null
            count > MAX_COUNT -> "$MAX_COUNT+"
            else -> count.toString()
        }

        val glyph = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.ic_notification_monitor,
            null,
        )
        // The glyph is centred in its own drawable and only moves aside, by as
        // much as the label needs, when there is a count to make room for.
        val glyphScale = when (label?.length) {
            null -> 1f
            1 -> 0.74f
            2 -> 0.64f
            else -> 0.56f
        }
        val glyphSize = (size * glyphScale).toInt()
        glyph?.setBounds(0, 0, glyphSize, glyphSize)
        glyph?.draw(canvas)

        if (label != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = when (label.length) {
                    1 -> size * 0.52f
                    2 -> size * 0.44f
                    else -> size * 0.36f
                }
                textAlign = Paint.Align.RIGHT
            }
            // Anchored to the bottom-right corner so a longer label grows into
            // the space the glyph gave up rather than off the edge.
            canvas.drawText(label, size * 0.99f, size * 0.98f, paint)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private const val ICON_SIZE_PX = 96
    private const val MAX_COUNT = 99
}
