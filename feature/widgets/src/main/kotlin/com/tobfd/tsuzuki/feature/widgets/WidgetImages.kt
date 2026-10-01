package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Images for the widgets, loaded through the app's Coil cache (so they show offline once seen) at the
 * size they are drawn. Widgets get bitmaps, not URLs: the launcher can't load images itself, and all
 * bitmaps of a widget together must stay small, so each is decoded at its drawn size only.
 */
internal suspend fun loadBitmap(context: Context, url: String?, width: Dp, height: Dp): Bitmap? {
    if (url == null) return null
    val density = context.resources.displayMetrics.density
    val request = ImageRequest.Builder(context)
        .data(url)
        .size((width.value * density).roundToInt(), (height.value * density).roundToInt())
        .scale(Scale.FILL)
        // RemoteViews can't carry hardware bitmaps.
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap()
}

/** [loadBitmap] for every url at once; the result maps each url to its bitmap (missing on failure). */
internal suspend fun loadBitmaps(
    context: Context,
    urls: Collection<String?>,
    width: Dp,
    height: Dp
): Map<String, Bitmap> = coroutineScope {
    urls.filterNotNull().distinct()
        .map { url -> async { url to loadBitmap(context, url, width, height) } }
        .awaitAll()
        .mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }
        .toMap()
}
