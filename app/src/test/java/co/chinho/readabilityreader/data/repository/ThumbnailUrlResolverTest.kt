package co.chinho.readabilityreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailUrlResolverTest {

    @Test
    fun `prefers source srcset over placeholder img src`() {
        val html = """
            <picture>
              <source
                srcset="https://asset.watch.impress.co.jp/img/ipw/docs/1680/111/main_1200.jpg 1200w, https://asset.watch.impress.co.jp/img/ipw/docs/1680/111/main_800.jpg 800w"
                type="image/jpeg"
              />
              <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAUEBA==" />
            </picture>
        """.trimIndent()

        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = null,
            proxyUrl = null,
            articleUrl = "https://www.watch.impress.co.jp/docs/news/1680111.html",
            readerBaseUrl = "https://reader.example.com",
            html = html,
        )

        assertEquals(
            "https://asset.watch.impress.co.jp/img/ipw/docs/1680/111/main_1200.jpg",
            result,
        )
    }

    @Test
    fun `uses lazy loaded image attributes when src is a spacer`() {
        val html = """
            <figure>
              <img
                src="/img/common/clear.gif"
                data-src="https://ascii.jp/img/2026/04/24/1234567/o/1200x675.jpg"
                alt="LOVE Walker"
              />
            </figure>
        """.trimIndent()

        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = null,
            proxyUrl = null,
            articleUrl = "https://lovewalker.jp/elem/000/004/358/4358976/",
            readerBaseUrl = "https://reader.example.com",
            html = html,
        )

        assertEquals(
            "https://ascii.jp/img/2026/04/24/1234567/o/1200x675.jpg",
            result,
        )
    }

    @Test
    fun `falls back to og image when no image tag is usable`() {
        val html = """
            <meta property="og:image" content="https://example.com/hero.jpg" />
            <img src="https://example.com/pixel.gif" />
        """.trimIndent()

        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = null,
            proxyUrl = null,
            articleUrl = "https://example.com/articles/1",
            readerBaseUrl = "https://reader.example.com",
            html = html,
        )

        assertEquals("https://example.com/hero.jpg", result)
    }

    @Test
    fun `prefers direct main image over flaky image proxy`() {
        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = "https://hobby.watch.impress.co.jp/img/hbw/list/2104/048/022.jpg",
            proxyUrl = "https://reader.example.com/api/reader/image-proxy?url=https%3A//hobby.watch.impress.co.jp/img/hbw/list/2104/048/022.jpg&w=800",
            articleUrl = "https://hobby.watch.impress.co.jp/docs/news/2104048.html",
            readerBaseUrl = "https://reader.example.com",
            html = null,
        )

        assertEquals(
            "https://hobby.watch.impress.co.jp/img/hbw/list/2104/048/022.jpg",
            result,
        )
    }

    @Test
    fun `prefers direct main image over cached image proxy`() {
        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = "https://img.toy-people.com/imgur/177700150844.png",
            proxyUrl = "https://reader.example.com/api/reader/cached-image/6c75f9819e865d8f64eab1a1096a7166.jpg",
            articleUrl = "https://www.toy-people.com/?p=123456",
            readerBaseUrl = "https://reader.example.com",
            html = null,
        )

        assertEquals(
            "https://img.toy-people.com/imgur/177700150844.png",
            result,
        )
    }

    @Test
    fun `prefers cached image raw url over html image`() {
        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = "https://reader.example.com/api/reader/cached-image/53eb7e1c538728b883d25717fa0acbdf.jpg",
            proxyUrl = null,
            articleUrl = "https://internet.watch.impress.co.jp/docs/column/security/1234567.html",
            readerBaseUrl = "https://reader.example.com",
            html = """
                <article>
                  <img src="https://asset.watch.impress.co.jp/img/iw/docs/1234/567/hero.jpg" />
                </article>
            """.trimIndent(),
        )

        assertEquals(
            "https://reader.example.com/api/reader/cached-image/53eb7e1c538728b883d25717fa0acbdf.jpg",
            result,
        )
    }

    @Test
    fun `returns null when only tracking images exist`() {
        val html = """
            <img src="https://sb.scorecardresearch.com/pixel" />
            <img src="https://www.google-analytics.com/collect" />
        """.trimIndent()

        val result = ThumbnailUrlResolver.resolveThumbnailUrl(
            rawUrl = null,
            proxyUrl = null,
            articleUrl = "https://example.com/articles/1",
            readerBaseUrl = "https://reader.example.com",
            html = html,
        )

        assertNull(result)
    }

    @Test
    fun `resolves favicon proxy as absolute when reader base url is provided`() {
        val result = ThumbnailUrlResolver.resolveFaviconUrl(
            feedId = 42L,
            feedUrl = "https://hobby.watch.impress.co.jp/feed/",
            siteUrl = "https://hobby.watch.impress.co.jp/",
            faviconUrl = null,
            faviconProxyUrl = "/api/reader/favicon-proxy/42",
            readerBaseUrl = "https://reader.example.com",
        )

        assertEquals("https://reader.example.com/api/reader/favicon-proxy/42", result)
    }

    @Test
    fun `prefers favicon proxy over raw favicon url`() {
        val result = ThumbnailUrlResolver.resolveFaviconUrl(
            feedId = 42L,
            feedUrl = "https://example.com/feed",
            siteUrl = "https://example.com",
            faviconUrl = "https://example.com/favicon.ico",
            faviconProxyUrl = "https://reader.example.com/api/reader/favicon-proxy/42",
            readerBaseUrl = "https://reader.example.com",
        )

        assertEquals(
            "https://reader.example.com/api/reader/favicon-proxy/42",
            result,
        )
    }

    @Test
    fun `falls back to readerBaseUrl favicon endpoint when nothing else is set`() {
        val result = ThumbnailUrlResolver.resolveFaviconUrl(
            feedId = 7L,
            feedUrl = "https://example.com/feed",
            siteUrl = null,
            faviconUrl = null,
            faviconProxyUrl = null,
            readerBaseUrl = "https://reader.example.com",
        )

        assertEquals("https://reader.example.com/api/reader/favicon/7", result)
    }

    @Test
    fun `falls back to google when no reader base url`() {
        val result = ThumbnailUrlResolver.resolveFaviconUrl(
            feedId = 7L,
            feedUrl = "https://www.example.com/feed",
            siteUrl = "https://www.example.com",
            faviconUrl = null,
            faviconProxyUrl = null,
            readerBaseUrl = null,
        )

        assertEquals(
            "https://www.google.com/s2/favicons?sz=64&domain_url=https://example.com",
            result,
        )
    }

    @Test
    fun `isValidSlideshowImageUrl filters out svg icons badges pixels and tracking URLs`() {
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/logo.svg"))
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/site_favicon.ico"))
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/user_avatar.jpg"))
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/badge_1x1.png"))
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/tracking_pixel.gif"))
        org.junit.Assert.assertFalse(ThumbnailUrlResolver.isValidSlideshowImageUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))

        org.junit.Assert.assertTrue(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://example.com/photo_large.jpg"))
        org.junit.Assert.assertTrue(ThumbnailUrlResolver.isValidSlideshowImageUrl("https://reader.example.com/api/reader/cached-image/abc12345.jpg"))
    }
}

