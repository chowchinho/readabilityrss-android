package co.chinho.readabilityreader.ui.reader

import android.content.Context
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Scale
import coil.size.Size

/** Aspect ratio assumed for a hero whose source dimensions are not known yet. */
const val HERO_FALLBACK_ASPECT = 16f / 9f

/** Share of the viewport a hero may fill before it is cropped instead of shown whole. */
const val HERO_MAX_VIEWPORT_FRACTION = 0.55f

data class HeroLayout(
    val heightDp: Float,
    val isCropped: Boolean,
)

fun computeHeroLayout(widthDp: Float, aspectRatio: Float?, maxHeightDp: Float): HeroLayout {
    if (widthDp <= 0f || maxHeightDp <= 0f) return HeroLayout(0f, false)
    val aspect = aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: HERO_FALLBACK_ASPECT
    val naturalHeight = widthDp / aspect
    return if (naturalHeight > maxHeightDp) {
        HeroLayout(maxHeightDp, true)
    } else {
        HeroLayout(naturalHeight, false)
    }
}

/**
 * Reads the cached file's header only, so the hero can reserve its real height on the first
 * frame rather than growing under the article once the bitmap arrives.
 */
@OptIn(ExperimentalCoilApi::class)
fun probeCachedAspect(imageLoader: ImageLoader, url: String): Float? {
    val snapshot = imageLoader.diskCache?.openSnapshot(url) ?: return null
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(snapshot.data.toFile().absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) null else w.toFloat() / h
    } catch (t: Throwable) {
        null
    } finally {
        snapshot.close()
    }
}

/**
 * Shared by the reader hero and the adjacent-article prewarm: both must build the request the
 * same way, or the prewarm decodes to a size the hero's own request will not reuse.
 *
 * Height is left undefined so the request scales to the display width alone. Pinning a size
 * here also keeps the request independent of the laid-out box, which grows once the aspect
 * ratio is known and would otherwise trigger a second load.
 */
fun buildHeroRequest(context: Context, url: String, widthPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(Size(Dimension.Pixels(widthPx.coerceAtLeast(1)), Dimension.Undefined))
        .scale(Scale.FIT)
        .build()
