package co.chinho.readabilityreader.ui.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LevelListDrawable
import android.os.SystemClock
import android.text.Html
import coil.annotation.ExperimentalCoilApi
import android.text.Layout
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BulletSpan
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.LeadingMarginSpan.Standard
import android.text.style.UnderlineSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.widget.TextViewCompat
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import co.chinho.readabilityreader.util.ExternalLinkOpener
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import java.net.URI
import kotlin.math.max

/** An extra entry on the text-selection menu. [onSelected] runs, then the selection closes. */
data class SelectionAction(val title: String, val onSelected: () -> Unit)

@Composable
fun HtmlContent(
    html: String,
    articleUrl: String,
    readerBaseUrl: String?,
    fontSize: TextUnit,
    fontFamily: String,
    externalLinkHandler: String,
    modifier: Modifier = Modifier,
    selectionActions: List<SelectionAction> = emptyList(),
) {
    val context = LocalContext.current
    val isEInk = LocalEInkMode.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val mutedTextColor = if (isEInk) {
        MaterialTheme.colorScheme.onSurface.toArgb()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    }
    val blockquoteBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val blockquoteBorderColor = if (isEInk) {
        MaterialTheme.colorScheme.onSurface.toArgb()
    } else {
        MaterialTheme.colorScheme.outline.toArgb()
    }
    val fontSizePx = with(LocalDensity.current) { fontSize.toPx() }
    val typeface = remember(fontFamily) { fontFamily.toTypeface() }
    val imageLoader = context.imageLoader
    val isPreview = LocalInspectionMode.current

    val imageGetter = remember(
        html,
        articleUrl,
        readerBaseUrl,
        fontSizePx,
        typeface,
        mutedTextColor,
        blockquoteBackgroundColor,
        blockquoteBorderColor,
        isEInk,
        isPreview,
    ) {
        CoilImageGetter(
            context = context,
            imageLoader = imageLoader,
            previewMode = isPreview,
            placeholderColor = blockquoteBackgroundColor,
            accentColor = blockquoteBorderColor,
        )
    }

    val parsedSpannable = remember(
        html,
        fontSizePx,
        typeface,
        textColor,
        linkColor,
        mutedTextColor,
        blockquoteBackgroundColor,
        blockquoteBorderColor,
        isEInk,
        articleUrl,
        readerBaseUrl,
        externalLinkHandler,
        imageGetter,
    ) {
        buildReaderSpannable(
            html = html,
            articleUrl = articleUrl,
            readerBaseUrl = readerBaseUrl,
            blockSpacingPx = (fontSizePx * 0.5f).toInt(),
            typeface = typeface,
            mutedTextColor = mutedTextColor,
            blockquoteBackgroundColor = blockquoteBackgroundColor,
            blockquoteBorderColor = blockquoteBorderColor,
            externalLinkHandler = externalLinkHandler,
            imageGetter = imageGetter,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                // setTextIsSelectable installs its own movement method, so it has to come first.
                setTextIsSelectable(true)
                movementMethod = SelectableLinkMovementMethod
                linksClickable = true
                includeFontPadding = false
            }
        },
        update = { textView ->
            imageGetter.textView = textView
            textView.customSelectionActionModeCallback =
                selectionActions.takeIf { it.isNotEmpty() }?.let { actions ->
                    object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            actions.forEachIndexed { index, action ->
                                // TextView orders its own items from 4 (Cut) upward and any
                                // PROCESS_TEXT app items from 100, so ordering below that puts
                                // these first; ALWAYS keeps them out of the overflow.
                                // The framework renders these as text whatever we set: only
                                // the leading assist slot ever draws an icon. Short titles keep
                                // room for the system's own items beside them.
                                menu.add(Menu.NONE, index, index + 1, action.title)
                                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                            }
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            val action = actions.getOrNull(item.itemId) ?: return false
                            action.onSelected()
                            mode.finish()
                            return true
                        }

                        override fun onDestroyActionMode(mode: ActionMode) = Unit
                    }
                }
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
            textView.setLineSpacing(fontSizePx * 0.3f, 1.0f)
            textView.typeface = typeface

            if (textView.tag != parsedSpannable) {
                textView.tag = parsedSpannable
                textView.text = parsedSpannable
            }

            // When getDrawable() runs synchronously inside HtmlCompat.fromHtml above,
            // textView.width is still 0, so resolveMaxWidth() falls back to the device
            // screen width. On phone that ≈ column width and is harmless; on tablet B-view
            // it's much wider than the actual reader column, so image spans get bounds
            // wider than the laid-out TextView and render clipped on the right. Re-parse
            // once after first layout so getDrawable() runs with the real column width.
            if (textView.width == 0) {
                textView.addOnLayoutChangeListener(
                    object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: View,
                            left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
                        ) {
                            if (v.width > 0) {
                                v.removeOnLayoutChangeListener(this)
                                val relaidOutSpannable = buildReaderSpannable(
                                    html = html,
                                    articleUrl = articleUrl,
                                    readerBaseUrl = readerBaseUrl,
                                    blockSpacingPx = (fontSizePx * 0.5f).toInt(),
                                    typeface = typeface,
                                    mutedTextColor = mutedTextColor,
                                    blockquoteBackgroundColor = blockquoteBackgroundColor,
                                    blockquoteBorderColor = blockquoteBorderColor,
                                    externalLinkHandler = externalLinkHandler,
                                    imageGetter = imageGetter,
                                )
                                // Deliberately does NOT touch the tag. The tag identifies which
                                // *parse* has been applied, not which CharSequence is showing, so
                                // the next update() must not see a mismatch and clobber this
                                // correctly-width-measured text with the screen-width parse.
                                // refreshTextView() relies on the same property.
                                textView.text = relaidOutSpannable
                            }
                        }
                    }
                )
            }
        }
    )
}

/**
 * The reader's whole HTML-to-spans pipeline, free of Compose and of any View, so the
 * render harness in `ReaderTypographyRenderTest` exercises exactly what ships.
 */
internal fun buildReaderSpannable(
    html: String,
    articleUrl: String,
    readerBaseUrl: String?,
    blockSpacingPx: Int,
    typeface: Typeface,
    mutedTextColor: Int,
    blockquoteBackgroundColor: Int,
    blockquoteBorderColor: Int,
    externalLinkHandler: String,
    imageGetter: Html.ImageGetter?,
): CharSequence {
    val parsedText = HtmlCompat.fromHtml(
        sanitizeHtml(
            html = html,
            articleUrl = articleUrl,
            readerBaseUrl = readerBaseUrl,
        ),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
        imageGetter,
        null,
    )
    return parsedText
        .normalizeListIndentation()
        .normalizeFontSizing()
        .styleBlockquotes(
            mutedTextColor = mutedTextColor,
            backgroundColor = blockquoteBackgroundColor,
            borderColor = blockquoteBorderColor,
        )
        .normalizeBlockSpacing(blockSpacingPx)
        .normalizeTypeface(typeface)
        .normalizeLinks()
        .trimNewlines()
        .withExternalLinks(externalLinkHandler)
}

internal fun sanitizeHtml(
    html: String,
    articleUrl: String,
    readerBaseUrl: String?,
): String {
    return html
        .replace(HTML_NOSCRIPT_REGEX, "")
        .replace(HTML_SCRIPT_REGEX, "")
        .replace(HTML_STYLE_TAG_REGEX, "")
        .replace(HTML_URL_ATTRIBUTE_REGEX) { match ->
            val attribute = match.groupValues[1]
            val quote = match.groupValues[2]
            val rawUrl = match.groupValues[3]
            val resolvedUrl = resolveHtmlUrl(
                rawUrl = rawUrl,
                articleUrl = articleUrl,
                readerBaseUrl = readerBaseUrl,
            )
            "$attribute=$quote$resolvedUrl$quote"
        }
        .replace(HTML_STRUCTURAL_WRAPPER_OPEN_REGEX, "")
        .replace(HTML_STRUCTURAL_WRAPPER_CLOSE_REGEX, "")
        .replace(HTML_BLOCKQUOTE_WRAPPED_PARAGRAPH_REGEX, "<blockquote>$1</blockquote>")
        .replace(HTML_IMAGE_ONLY_PARAGRAPH_REGEX, "$1")
        .replace(HTML_LINKED_IMAGE_ONLY_PARAGRAPH_REGEX, "$1")
        .replace(HTML_PICTURE_ONLY_PARAGRAPH_REGEX, "$1")
        .replace(HTML_FIGURE_OPEN_REGEX, "")
        .replace(HTML_FIGURE_CLOSE_REGEX, "")
        .replace(HTML_IMAGE_TAG_REGEX) { match -> "<br>${match.value}<br>" }
        .replace(HTML_STYLE_ATTRIBUTE_REGEX, "")
        .replace(HTML_PRESENTATIONAL_ATTRIBUTE_REGEX, "")
        .replace(HTML_DATA_ATTRIBUTE_REGEX, "")
        .replace(HTML_CLASS_OR_ID_ATTRIBUTE_REGEX, "")
        .replace(HTML_ARIA_AND_ROLE_ATTRIBUTE_REGEX, "")
        .replace("<br></p>", "</p>")
        .replace("<p></p>", "")
        .replace(HTML_EMPTY_PARAGRAPH_REGEX, "")
        .replace(HTML_EMPTY_BLOCKQUOTE_REGEX, "")
        .replace(HTML_WHITESPACE_BETWEEN_TAGS_REGEX, "><")
        .replace(HTML_MULTIPLE_WHITESPACE_REGEX, " ")
        .trim()
}

private fun resolveHtmlUrl(
    rawUrl: String,
    articleUrl: String,
    readerBaseUrl: String?,
): String {
    val candidate = rawUrl.trim()
    if (candidate.isBlank()) return rawUrl
    if (
        candidate.startsWith("http://", ignoreCase = true) ||
        candidate.startsWith("https://", ignoreCase = true) ||
        candidate.startsWith("data:", ignoreCase = true) ||
        candidate.startsWith("mailto:", ignoreCase = true) ||
        candidate.startsWith("tel:", ignoreCase = true) ||
        candidate.startsWith("#") ||
        candidate.startsWith("javascript:", ignoreCase = true)
    ) {
        return candidate
    }
    if (candidate.startsWith("//")) {
        return "https:$candidate"
    }
    if (candidate.startsWith("/api/reader/") && !readerBaseUrl.isNullOrBlank()) {
        return "$readerBaseUrl$candidate"
    }
    return runCatching { URI(articleUrl).resolve(candidate).toString() }
        .getOrElse { rawUrl }
}

private fun String.toTypeface(): Typeface {
    return when (this.lowercase()) {
        "serif" -> Typeface.SERIF
        "sans-serif", "sans serif" -> Typeface.SANS_SERIF
        "monospace" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }
}

private fun Spanned.withExternalLinks(
    externalLinkHandler: String,
): SpannableString {
    val spannable = SpannableString(this)
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            ExternalLinkSpan(
                url = span.url,
                externalLinkHandler = externalLinkHandler,
            ),
            start,
            end,
            flags,
        )
    }
    return spannable
}

private fun Spanned.styleBlockquotes(
    mutedTextColor: Int,
    backgroundColor: Int,
    borderColor: Int,
): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    spannable.getSpans(0, spannable.length, QuoteSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            ReaderBlockquoteSpan(
                stripeColor = borderColor,
                backgroundColor = backgroundColor,
            ),
            start,
            end,
            flags,
        )
        spannable.setSpan(ForegroundColorSpan(mutedTextColor), start, end, flags)
    }
    return spannable
}

private fun Spanned.normalizeFontSizing(): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)

    // Remove any hard-coded absolute sizes (e.g. from inline 'style="font-size:..."')
    spannable.getSpans(0, spannable.length, AbsoluteSizeSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }

    spannable.getSpans(0, spannable.length, SubscriptSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }

    spannable.getSpans(0, spannable.length, SuperscriptSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }

    // For relative sizes (like 0.85em), we keep the proportion but clamp it
    // so it doesn't get too tiny or too huge relative to the user's base font size.
    spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        val proportion = span.sizeChange
        
        spannable.removeSpan(span)
        
        // Clamp between 0.8x and 1.25x of the base font size
        val clampedProportion = proportion.coerceIn(0.8f, 1.25f)
        if (clampedProportion != 1.0f) {
            spannable.setSpan(RelativeSizeSpan(clampedProportion), start, end, flags)
        }
    }

    return spannable
}

private fun Spanned.normalizeLinks(): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    spannable.getSpans(0, spannable.length, UnderlineSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }
    return spannable
}

private fun Spanned.normalizeListIndentation(): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)

    spannable.getSpans(0, spannable.length, BulletSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            BulletSpan(12, 0, 6),
            start,
            end,
            flags,
        )
    }

    spannable.getSpans(0, spannable.length, LeadingMarginSpan::class.java)
        .filterIsInstance<Standard>()
        .forEach { span ->
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val flags = spannable.getSpanFlags(span)
            val first = span.getLeadingMargin(true)
            val rest = span.getLeadingMargin(false)
            val normalizedFirst = when {
                first >= 96 -> 40
                first >= 72 -> 32
                first >= 48 -> 24
                else -> first
            }
            val normalizedRest = when {
                rest >= 96 -> 40
                rest >= 72 -> 32
                rest >= 48 -> 24
                else -> rest
            }
            if (normalizedFirst != first || normalizedRest != rest) {
                spannable.removeSpan(span)
                spannable.setSpan(
                    Standard(normalizedFirst, normalizedRest),
                    start,
                    end,
                    flags,
                )
            }
        }

    return spannable
}

private fun Spanned.normalizeBlockSpacing(blockSizePx: Int): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    var i = 0
    while (i < spannable.length - 1) {
        if (spannable[i] == '\n' && spannable[i + 1] == '\n') {
            spannable.setSpan(
                AbsoluteSizeSpan(blockSizePx),
                i + 1,
                i + 2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i += 2
        } else {
            i++
        }
    }
    return spannable
}

private fun Spanned.trimNewlines(): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)
    while (spannable.isNotEmpty() && spannable[0] == '\n') {
        spannable.delete(0, 1)
    }
    while (spannable.isNotEmpty() && spannable[spannable.length - 1] == '\n') {
        spannable.delete(spannable.length - 1, spannable.length)
    }
    return spannable
}

private fun Spanned.normalizeTypeface(typeface: Typeface): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(this)

    // Remove all existing TypefaceSpans added by Html.fromHtml (which would override our base)
    spannable.getSpans(0, spannable.length, TypefaceSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }

    // Apply our selected typeface across the entire range
    if (spannable.isNotEmpty()) {
        spannable.setSpan(
            CustomTypefaceSpan(typeface),
            0,
            spannable.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE,
        )
    }

    return spannable
}


@OptIn(ExperimentalCoilApi::class)
private class CoilImageGetter(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val previewMode: Boolean,
    private val placeholderColor: Int,
    private val accentColor: Int,
    var textView: TextView? = null,
) : Html.ImageGetter {

    override fun getDrawable(source: String?): Drawable {
        if (previewMode) {
            return PreviewPlaceholderDrawable(
                backgroundColor = placeholderColor,
                accentColor = accentColor,
            ).apply {
                val width = resolveMaxWidth()
                val height = max(1, width * 9 / 16)
                setBounds(0, 0, width, height)
            }
        }

        val placeholder = UrlDrawable(
            placeholderColor = placeholderColor,
            spinnerColor = accentColor,
        )

        if (source.isNullOrBlank()) {
            placeholder.setBounds(0, 0, 1, 1)
            return placeholder
        }

        val maxWidth = resolveMaxWidth()
        val cachedHeight = probeCachedHeight(source, maxWidth)
        val initialHeight = cachedHeight ?: max(1, maxWidth / 2)
        placeholder.setBounds(0, 0, maxWidth, initialHeight)

        val request = ImageRequest.Builder(context)
            .data(source)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    val bounded = RoundedDrawable(drawable.mutate(), cornerRadiusPx = 6f)
                    val width = bounded.intrinsicWidth.takeIf { it > 0 } ?: 1
                    val height = bounded.intrinsicHeight.takeIf { it > 0 } ?: 1
                    val currentMaxWidth = resolveMaxWidth()
                    val scaledHeight = max(1, height * currentMaxWidth / width)
                    bounded.setBounds(0, 0, currentMaxWidth, scaledHeight)
                    placeholder.setDrawable(bounded)
                    if (cachedHeight == null || cachedHeight != scaledHeight) {
                        refreshTextView()
                    }
                },
                onError = {
                    placeholder.setBounds(0, 0, 1, 1)
                    refreshTextView()
                }
            )
            .build()

        imageLoader.enqueue(request)
        return placeholder
    }

    private fun probeCachedHeight(source: String, maxWidth: Int): Int? {
        val diskCache = imageLoader.diskCache ?: return null
        val snapshot = diskCache.openSnapshot(source) ?: return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(snapshot.data.toFile().absolutePath, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) null else max(1, h * maxWidth / w)
        } catch (t: Throwable) {
            null
        } finally {
            snapshot.close()
        }
    }

    private fun resolveMaxWidth(): Int {
        val tv = textView
        if (tv != null) {
            val availableWidth = tv.width - tv.paddingLeft - tv.paddingRight
            if (availableWidth > 0) {
                return availableWidth
            }
            val screenWidth = tv.resources.displayMetrics.widthPixels
            return max(1, screenWidth - tv.paddingLeft - tv.paddingRight)
        }
        val screenWidth = context.resources.displayMetrics.widthPixels
        return max(1, screenWidth)
    }

    private fun refreshTextView() {
        textView?.post {
            val current = textView?.text
            if (current is Spanned) {
                textView?.setText(
                    SpannableStringBuilder(current),
                    TextView.BufferType.SPANNABLE,
                )
            }
        }
    }
}

private class PreviewPlaceholderDrawable(
    private val backgroundColor: Int,
    private val accentColor: Int,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = accentColor
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = accentColor
        alpha = 180
    }

    override fun draw(canvas: Canvas) {
        val rect = bounds
        if (rect.isEmpty) return

        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)

        val inset = rect.width() * 0.08f
        val left = rect.left + inset
        val right = rect.right - inset
        val top = rect.top + inset
        val bottom = rect.bottom - inset
        canvas.drawLine(left, bottom, right, top, linePaint)
        canvas.drawLine(left, top, right, bottom, linePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        linePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        linePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/**
 * Long-press selection and tappable links at the same time.
 *
 * `LinkMovementMethod` consumes the touch stream that selection needs, which is why long-press
 * did nothing. `ArrowKeyMovementMethod` is the one that supports selection, so links are
 * dispatched on top of it: a tap that lands on a span with nothing selected opens the link,
 * everything else falls through to the selection behaviour.
 */
private object SelectableLinkMovementMethod : ArrowKeyMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !widget.hasSelection()) {
            linkAt(widget, buffer, event)?.let { span ->
                span.onClick(widget)
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun linkAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val line = layout.getLineForVertical(y.toInt())
        // getOffsetForHorizontal clamps to the line, so a tap past the end of a short line would
        // otherwise resolve onto whatever span ends there.
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x)
        return buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }
}

private class ExternalLinkSpan(
    private val url: String,
    private val externalLinkHandler: String,
) : URLSpan(url) {
    override fun onClick(widget: android.view.View) {
        ExternalLinkOpener.open(widget.context, url, externalLinkHandler)
    }
}

private class UrlDrawable(
    private val placeholderColor: Int,
    private val spinnerColor: Int,
) : LevelListDrawable() {
    private var drawable: Drawable? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = placeholderColor
    }
    private val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        color = spinnerColor
    }
    private val spinnerRect = RectF()
    private val startTime = SystemClock.uptimeMillis()

    fun setDrawable(drawable: Drawable) {
        this.drawable = drawable
        setBounds(drawable.bounds)
    }

    override fun draw(canvas: Canvas) {
        drawable?.let {
            it.draw(canvas)
            return
        }
        val b = bounds
        if (b.isEmpty || b.width() < 8 || b.height() < 8) return
        canvas.drawRect(b, fillPaint)
        val radius = (minOf(b.width(), b.height()) * 0.12f).coerceIn(12f, 40f)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        spinnerRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val elapsed = SystemClock.uptimeMillis() - startTime
        val startAngle = (elapsed * 360f / 1200f) % 360f
        canvas.drawArc(spinnerRect, startAngle, 90f, false, spinnerPaint)
        invalidateSelf()
    }
}

private class RoundedDrawable(
    private val delegate: Drawable,
    private val cornerRadiusPx: Float,
) : Drawable() {
    private val clipPath = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        delegate.bounds = bounds
        rect.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        delegate.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) {
        delegate.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        delegate.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = delegate.opacity

    override fun getIntrinsicWidth(): Int = delegate.intrinsicWidth

    override fun getIntrinsicHeight(): Int = delegate.intrinsicHeight
}

private val HTML_URL_ATTRIBUTE_REGEX =
    Regex("(src|href)\\s*=\\s*([\"'])([^\"']+)\\2", RegexOption.IGNORE_CASE)
private val HTML_BLOCKQUOTE_WRAPPED_PARAGRAPH_REGEX =
    Regex("<blockquote\\b[^>]*>\\s*<p\\b[^>]*>(.*?)</p>\\s*</blockquote>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_IMAGE_ONLY_PARAGRAPH_REGEX =
    Regex("<p>\\s*(<img\\b[^>]*>)\\s*</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_LINKED_IMAGE_ONLY_PARAGRAPH_REGEX =
    Regex("<p>\\s*(<a\\b[^>]*>\\s*<img\\b[^>]*>\\s*</a>)\\s*</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_PICTURE_ONLY_PARAGRAPH_REGEX =
    Regex("<p>\\s*(<picture\\b[^>]*>.*?</picture>)\\s*</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_IMAGE_TAG_REGEX =
    Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_FIGURE_OPEN_REGEX =
    Regex("<figure\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_FIGURE_CLOSE_REGEX =
    Regex("</figure>", RegexOption.IGNORE_CASE)
private val HTML_STRUCTURAL_WRAPPER_OPEN_REGEX =
    Regex("<(?:section|article|header|footer|main|aside|div|nav)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_STRUCTURAL_WRAPPER_CLOSE_REGEX =
    Regex("</(?:section|article|header|footer|main|aside|div|nav)>", RegexOption.IGNORE_CASE)
private val HTML_NOSCRIPT_REGEX =
    Regex("<noscript\\b[^>]*>.*?</noscript>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_SCRIPT_REGEX =
    Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_STYLE_TAG_REGEX =
    Regex("<style\\b[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_STYLE_ATTRIBUTE_REGEX =
    Regex("\\sstyle\\s*=\\s*([\"']).*?\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_PRESENTATIONAL_ATTRIBUTE_REGEX =
    Regex("\\s(?:bgcolor|border|cellpadding|cellspacing|width|height|align|valign)\\s*=\\s*([\"']).*?\\1", RegexOption.IGNORE_CASE)
private val HTML_DATA_ATTRIBUTE_REGEX =
    Regex("\\sdata-[a-z0-9_-]+\\s*=\\s*([\"']).*?\\1", RegexOption.IGNORE_CASE)
private val HTML_CLASS_OR_ID_ATTRIBUTE_REGEX =
    Regex("\\s(?:class|id)\\s*=\\s*([\"']).*?\\1", RegexOption.IGNORE_CASE)
private val HTML_ARIA_AND_ROLE_ATTRIBUTE_REGEX =
    Regex("\\s(?:aria-[a-z0-9_-]+|role)\\s*=\\s*([\"']).*?\\1", RegexOption.IGNORE_CASE)
private val HTML_EMPTY_PARAGRAPH_REGEX =
    Regex("<p>(?:\\s|&nbsp;|<br\\s*/?>)*</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_EMPTY_BLOCKQUOTE_REGEX =
    Regex("<blockquote>\\s*</blockquote>", RegexOption.IGNORE_CASE)
private val HTML_WHITESPACE_BETWEEN_TAGS_REGEX =
    Regex(">\\s+<", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_MULTIPLE_WHITESPACE_REGEX =
    Regex("[\\t\\x0B\\f\\r \\n]{2,}")

private class CustomTypefaceSpan(
    private val typeface: Typeface,
) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(textPaint: android.text.TextPaint) {
        textPaint.typeface = typeface
    }

    override fun updateMeasureState(textPaint: android.text.TextPaint) {
        textPaint.typeface = typeface
    }
}

private class ReaderBlockquoteSpan(
    private val stripeColor: Int,
    private val backgroundColor: Int,
) : LeadingMarginSpan, LineBackgroundSpan {

    private val marginPx = 12
    private val stripeWidthPx = 2
    private val stripeGapPx = 14

    override fun getLeadingMargin(first: Boolean): Int {
        return marginPx + stripeWidthPx + stripeGapPx
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        val previousStyle = p.style
        val previousColor = p.color
        p.style = Paint.Style.FILL
        p.color = stripeColor
        val stripeLeft = x + (dir * marginPx)
        val stripeRight = stripeLeft + (dir * stripeWidthPx)
        c.drawRect(
            minOf(stripeLeft, stripeRight).toFloat(),
            top.toFloat(),
            maxOf(stripeLeft, stripeRight).toFloat(),
            bottom.toFloat(),
            p,
        )
        p.style = previousStyle
        p.color = previousColor
    }

    override fun drawBackground(
        c: Canvas,
        p: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lnum: Int,
    ) {
        val previousColor = p.color
        p.color = backgroundColor
        c.drawRect(
            (left + marginPx).toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat(),
            p,
        )
        p.color = previousColor
    }
}

@Preview(showBackground = true, name = "Reader typography", widthDp = 412, heightDp = 4000)
@Composable
private fun HtmlContentPreview() {
    MaterialTheme {
        HtmlContent(
            html = READER_TYPOGRAPHY_SPECIMEN,
            articleUrl = "https://example.com/article",
            readerBaseUrl = "https://reader.example.com",
            fontSize = 18.sp,
            fontFamily = "sans-serif",
            externalLinkHandler = "custom_tabs",
        )
    }
}
