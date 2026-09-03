package co.chinho.readabilityreader.ui.reader

/**
 * A fixed article body covering every structure the reader has to typeset, used by the
 * `@Preview` in [HtmlContent] and by the render harness in `ReaderTypographyRenderTest`.
 *
 * The bilingual shape is not invented: the backend's `_interleave_translation` replaces
 * each block element with `<blockquote>original</blockquote>` followed by the translated
 * element of the same tag, and `<li>` is one of the tags it does that to. So a translated
 * list really does arrive as `<blockquote><li>…</li></blockquote><li>…</li>` inside the
 * list — markup no browser or parser considers legal, and the case the web reader's
 * list rules were rewritten around.
 */
internal const val READER_TYPOGRAPHY_SPECIMEN: String = """
<h1>Heading level one</h1>
<p>A plain untranslated paragraph, with <a href="https://example.com">an inline link</a>,
some <b>bold</b> and some <i>italic</i>, long enough to wrap onto a second line so line
spacing and the left edge of the text column are both visible.</p>

<h2>Heading level two</h2>

<blockquote><p>The original-language paragraph, which the reader shows above its
translation rather than as a quotation.</p></blockquote>
<p>原文段落，閱讀器將它顯示在譯文上方，而不是當作引文處理。</p>

<blockquote><p>A second original, immediately after the first pair, to show how much
space separates one pair from the next.</p></blockquote>
<p>緊接在第一組之後的第二段原文，用來顯示每組之間的間距。</p>

<blockquote><h3>An original-language heading</h3></blockquote>
<h3>原文標題</h3>

<p>An untranslated list follows, so the plain marker is visible on its own:</p>

<ul>
<li>First item in a list that was never translated.</li>
<li>Second item, made long enough that it wraps and shows whether the runover
aligns with the first line's text or slides back under the marker.</li>
<li>Third item, with a nested list under it:
<ul><li>A nested item.</li><li>Another nested item.</li></ul></li>
</ul>

<h2>Translated lists</h2>

<p>A translated unordered list, in the interleaved shape the backend emits:</p>

<ul>
<blockquote><li>The first original item.</li></blockquote>
<li>第一個原文項目。</li>
<blockquote><li>The second original item, long enough to wrap so the alignment of its
runover line against the translation's text column is visible.</li></blockquote>
<li>第二個原文項目，長度足以換行，以便看出續行與譯文文字欄位的對齊情況。</li>
</ul>

<p>A translated ordered list. Four steps — the numbering must read 1 to 4, not 1 to 8:</p>

<ol>
<blockquote><li>Open the settings screen.</li></blockquote>
<li>開啟設定畫面。</li>
<blockquote><li>Choose a sync interval.</li></blockquote>
<li>選擇同步間隔。</li>
<blockquote><li>Enable background sync.</li></blockquote>
<li>啟用背景同步。</li>
<blockquote><li>Return to the article list.</li></blockquote>
<li>返回文章列表。</li>
</ol>

<p>An untranslated ordered list, for comparison:</p>

<ol>
<li>Step one.</li>
<li>Step two.</li>
<li>Step three, long enough to wrap and show how the runover line sits relative to
the numeral in the gutter.</li>
</ol>

<h4>Heading level four</h4>
<p>Body text under a level-four heading.</p>

<h5>Heading level five</h5>
<p>Body text under a level-five heading.</p>

<dl>
<dt>Display</dt>
<dd>6.7-inch foldable, 120 Hz.</dd>
<dt>Battery</dt>
<dd>4300 mAh.</dd>
<dt>Term with no definition</dt>
<dd></dd>
</dl>

<h6>Heading level six</h6>
<p>The final paragraph, closing the specimen.</p>
"""
