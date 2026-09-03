package co.chinho.readabilityreader.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment

const val MAX_SLIDES = 5
const val SLIDE_HOLD_MS = 4000L
const val MAX_STAGGER_MS = 1500L
const val CROSS_FADE_MS = 1200

data class SlideshowImage(
    val src: String,
    val focalX: Int = 50,
    val focalY: Int = 50,
)

fun getFocalAlignment(focalX: Int, focalY: Int): Alignment =
    BiasAlignment((focalX - 50) / 50f, (focalY - 50) / 50f)

fun buildSlides(
    cover: SlideshowImage?,
    extras: List<SlideshowImage>,
    cap: Int = MAX_SLIDES,
): List<SlideshowImage> {
    if (cover == null) return emptyList()
    return (listOf(cover) + extras).distinctBy { it.src }.take(cap)
}

/**
 * Every card on screen becomes visible on the same frame, so without a per-card offset they
 * transition in lockstep and their decodes contend for the same four Coil slots. Article ids
 * are consecutive, so this needs an avalanche mix (splitmix64) rather than `id % n`.
 */
fun staggerMs(articleId: Long): Long {
    var x = articleId
    x = x xor (x ushr 33)
    x *= 0xff51afd7ed558ccdUL.toLong()
    x = x xor (x ushr 33)
    x *= 0xc4ceb9fe1a85ec53UL.toLong()
    x = x xor (x ushr 33)
    return (x and Long.MAX_VALUE) % MAX_STAGGER_MS
}
