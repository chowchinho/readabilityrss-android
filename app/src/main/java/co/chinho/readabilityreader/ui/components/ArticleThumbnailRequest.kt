package co.chinho.readabilityreader.ui.components

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import kotlin.math.roundToInt

/**
 * Coil request geometry shared by the article-list rows and the ViewModel prefetch pass.
 *
 * No transformations: Coil only stores the request size in the memory-cache key when a
 * transformation is present, and `MemoryCacheService.isSizeValid` then requires an exact
 * string match. Keeping the key at the bare URL is what makes a prefetch/row mismatch
 * impossible. Framing is applied at render time from the stored focal point instead.
 */
object ArticleThumbnailRequest {

    private const val STANDARD_THUMB_DP = 96f
    private const val FULL_IMAGE_HEIGHT_DP = 220f

    data class Spec(val widthPx: Int, val heightPx: Int)

    fun specFor(context: Context, fullImageMode: Boolean): Spec {
        val metrics = context.resources.displayMetrics
        return if (fullImageMode) {
            Spec(
                widthPx = metrics.widthPixels,
                heightPx = (FULL_IMAGE_HEIGHT_DP * metrics.density).roundToInt(),
            )
        } else {
            val px = (STANDARD_THUMB_DP * metrics.density).roundToInt()
            Spec(widthPx = px, heightPx = px)
        }
    }

    fun build(
        context: Context,
        url: String,
        spec: Spec,
    ): ImageRequest = ImageRequest.Builder(context)
        .data(url)
        .size(spec.widthPx, spec.heightPx)
        .scale(Scale.FILL)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
}
