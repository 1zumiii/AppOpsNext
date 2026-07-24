package dev.izumi.appopsnext.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.izumi.appopsnext.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(
    packageName: String,
    appLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = DefaultAppIconSize,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = produceState<ImageBitmap?>(
        initialValue = AppIconCache[packageName],
        key1 = packageName,
        key2 = sizePx,
    ) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            AppIconCache[packageName]
                ?: runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .render(sizePx)
                        .also { AppIconCache.put(packageName, it) }
                }.getOrNull()
        }
    }
    val description = stringResource(R.string.app_icon_description, appLabel)

    if (bitmap.value != null) {
        Image(
            bitmap = checkNotNull(bitmap.value),
            contentDescription = description,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_default_app),
                contentDescription = description,
                modifier = Modifier.size(size * DEFAULT_ICON_SCALE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Drawable.render(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        sizePx,
        sizePx,
        Bitmap.Config.ARGB_8888,
    )
    val previousBounds = copyBounds()
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    bounds = previousBounds
    return bitmap.asImageBitmap()
}

private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(MAX_CACHED_ICONS)

    operator fun get(packageName: String): ImageBitmap? =
        synchronized(cache) { cache[packageName] }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) {
            cache.put(packageName, bitmap)
        }
    }

    private const val MAX_CACHED_ICONS = 128
}

private val DefaultAppIconSize = 44.dp
private const val DEFAULT_ICON_SCALE = 0.58f
