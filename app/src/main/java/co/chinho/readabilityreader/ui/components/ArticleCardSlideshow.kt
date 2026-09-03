package co.chinho.readabilityreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

private const val READY_TIMEOUT_MS = 3_000L

private enum class SlideshowGate {
    /** E-Ink: slide 0 only, no timers. */
    Frozen,

    /** Visible but not eligible to advance — cover still loading, or armed and then scrolled. */
    Hold,

    /** Counting down or cycling. */
    Run,

    /** Fully off screen: back to the cover. */
    Reset,
}

@Composable
fun ArticleCardSlideshow(
    articleId: Long,
    mainImage: String?,
    mainFocalX: Int = 50,
    mainFocalY: Int = 50,
    imageAlpha: Float = 1.0f,
    isEInk: Boolean = false,
    fullImageMode: Boolean = false,
    onFetchExtraImages: (suspend (Long) -> List<SlideshowImage>)? = null,
    modifier: Modifier = Modifier,
) {
    val cover = remember(mainImage, mainFocalX, mainFocalY) {
        mainImage?.takeIf { it.isNotBlank() }?.let { SlideshowImage(it, mainFocalX, mainFocalY) }
    }

    var slides by remember(articleId, cover) { mutableStateOf(listOfNotNull(cover)) }
    var coverShown by remember(articleId, cover) { mutableStateOf(cover == null) }
    var hasStartedCycling by remember(articleId, cover) { mutableStateOf(false) }

    var slotASlide by remember(articleId, cover) { mutableStateOf(cover) }
    var slotBSlide by remember(articleId, cover) { mutableStateOf<SlideshowImage?>(null) }
    val slotBAlpha = remember(articleId, cover) { Animatable(0f) }
    var readySrcs by remember(articleId, cover) { mutableStateOf(emptySet<String>()) }

    var isFullyOnScreen by remember { mutableStateOf(false) }
    var isPartiallyOnScreen by remember { mutableStateOf(false) }

    val visibilityModifier = Modifier.onGloballyPositioned { coordinates ->
        val rootBounds = coordinates.findRootCoordinates().boundsInWindow()
        val bounds = coordinates.boundsInWindow()

        if (coordinates.size.height > 0 && rootBounds.height > 0) {
            isFullyOnScreen = bounds.top >= rootBounds.top - 1f && bounds.bottom <= rootBounds.bottom + 1f
            isPartiallyOnScreen = bounds.bottom > rootBounds.top && bounds.top < rootBounds.bottom
        }
    }

    // A derived enum, not the raw booleans. The effect below writes `hasStartedCycling`, which
    // is read here — but the branch it moves between produces the same `Run` value, so the
    // effect is never cancelled by its own write. Keying on the booleans directly is what made
    // the first transition land at 8.1s instead of 4.0s.
    val gate = when {
        isEInk -> SlideshowGate.Frozen
        !isPartiallyOnScreen -> SlideshowGate.Reset
        hasStartedCycling -> SlideshowGate.Run
        isFullyOnScreen && coverShown -> SlideshowGate.Run
        else -> SlideshowGate.Hold
    }

    suspend fun awaitReady(src: String) {
        withTimeoutOrNull(READY_TIMEOUT_MS) {
            snapshotFlow { readySrcs }.first { src in it }
        }
    }

    LaunchedEffect(articleId, gate) {
        when (gate) {
            SlideshowGate.Frozen, SlideshowGate.Hold -> return@LaunchedEffect

            SlideshowGate.Reset -> {
                hasStartedCycling = false
                slides = listOfNotNull(cover)
                slotASlide = cover
                slotBSlide = null
                slotBAlpha.snapTo(0f)
                return@LaunchedEffect
            }

            SlideshowGate.Run -> Unit
        }

        // Rule 4: the slide list and slide 1 are only fetched once the cover is up and the card
        // is fully visible. Cancelling this effect (Rule 6) cancels the fetch with it.
        if (slides.size <= 1 && onFetchExtraImages != null) {
            slides = buildSlides(cover, onFetchExtraImages(articleId))
        }
        if (slides.size <= 1) return@LaunchedEffect  // Rule 2

        var currentIdx = 0
        var showingB = false
        slotBSlide = slides[1]

        delay(SLIDE_HOLD_MS + staggerMs(articleId))

        while (isActive) {
            hasStartedCycling = true

            val nextIdx = (currentIdx + 1) % slides.size
            val next = slides[nextIdx]

            // Slot B is composed second and always draws on top, so only its alpha animates.
            // Fading B in reveals the next slide over A; fading B out reveals the next slide
            // already staged on A underneath. Animating the incoming slot instead would be
            // invisible beneath an opaque B and would land as a hard cut.
            if (showingB) {
                slotASlide = next
                awaitReady(next.src)
                slotBAlpha.animateTo(0f, tween(CROSS_FADE_MS, easing = LinearEasing))
            } else {
                slotBSlide = next
                awaitReady(next.src)
                slotBAlpha.animateTo(1f, tween(CROSS_FADE_MS, easing = LinearEasing))
            }

            showingB = !showingB
            currentIdx = nextIdx
            delay(SLIDE_HOLD_MS)
        }
    }

    Box(modifier = modifier.then(visibilityModifier).clipToBounds()) {
        val primary = slotASlide

        if (primary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Newspaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp),
                )
            }
        } else {
            // Slot A stays fully opaque throughout, so the card background never bleeds through.
            SlideshowImageItem(
                slide = primary,
                imageAlpha = imageAlpha,
                isEInk = isEInk,
                fullImageMode = fullImageMode,
                onReady = { src ->
                    readySrcs = readySrcs + src
                    if (src == cover?.src) coverShown = true
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (!isEInk && slides.size > 1) {
                slotBSlide?.let { slide ->
                    SlideshowImageItem(
                        slide = slide,
                        imageAlpha = imageAlpha * slotBAlpha.value,
                        isEInk = false,
                        fullImageMode = fullImageMode,
                        onReady = { src -> readySrcs = readySrcs + src },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}


@Composable
private fun SlideshowImageItem(
    slide: SlideshowImage,
    imageAlpha: Float,
    isEInk: Boolean,
    fullImageMode: Boolean,
    modifier: Modifier = Modifier,
    onReady: ((String) -> Unit)? = null,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }

    val context = LocalContext.current
    val request = remember(slide.src, fullImageMode) {
        ArticleThumbnailRequest.build(
            context = context,
            url = slide.src,
            spec = ArticleThumbnailRequest.specFor(context, fullImageMode),
        )
    }

    val alignment = getFocalAlignment(slide.focalX, slide.focalY)

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        alpha = imageAlpha,
        onSuccess = { onReady?.invoke(slide.src) },
        onError = { onReady?.invoke(slide.src) },
        colorFilter = if (isEInk) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else null
    )
}
