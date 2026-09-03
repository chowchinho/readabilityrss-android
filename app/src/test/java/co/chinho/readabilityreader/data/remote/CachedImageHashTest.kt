package co.chinho.readabilityreader.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedImageHashTest {

    @Test
    fun `extracts hash from cached-image jpg url`() {
        val url = "https://reader.example.com/api/reader/cached-image/4a3a78b7a87d7c0bf5e436d2c1fd0cf2.jpg"
        assertEquals("4a3a78b7a87d7c0bf5e436d2c1fd0cf2", CachedImageHash.fromUrl(url))
    }

    @Test
    fun `extracts hash from cached-image png url`() {
        val url = "https://reader.example.com/api/reader/cached-image/b89d6e4f3a2c1b0e9d8c7b6a5f4e3d2c.png"
        assertEquals("b89d6e4f3a2c1b0e9d8c7b6a5f4e3d2c", CachedImageHash.fromUrl(url))
    }

    @Test
    fun `extracts hash from cached-image webp url`() {
        val url = "https://reader.example.com/api/reader/cached-image/11223344556677889900aabbccddeeff.webp"
        assertEquals("11223344556677889900aabbccddeeff", CachedImageHash.fromUrl(url))
    }

    @Test
    fun `normalizes uppercase hex to lowercase`() {
        val url = "https://reader.example.com/api/reader/cached-image/4A3A78B7A87D7C0BF5E436D2C1FD0CF2.JPG"
        assertEquals("4a3a78b7a87d7c0bf5e436d2c1fd0cf2", CachedImageHash.fromUrl(url))
    }

    @Test
    fun `bare origin url returns null`() {
        val url = "https://example.com/images/feature.jpg"
        assertNull(CachedImageHash.fromUrl(url))
    }

    @Test
    fun `image-proxy url returns null`() {
        val url = "https://reader.example.com/api/reader/image-proxy?url=https%3A%2F%2Fexample.com%2Fphoto.jpg"
        assertNull(CachedImageHash.fromUrl(url))
    }

    @Test
    fun `empty url returns null`() {
        assertNull(CachedImageHash.fromUrl(""))
    }
}
