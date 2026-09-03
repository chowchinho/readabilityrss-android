package co.chinho.readabilityreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageStatusTextTest {

    @Test
    fun `a single active phase stays on one line`() {
        val status = SyncStatus(checkedImages = 12, totalImages = 30)
        assertEquals("Checking images (12/30)", imageStatusText(status))
    }

    @Test
    fun `no image work returns null`() {
        assertNull(imageStatusText(SyncStatus()))
    }

    @Test
    fun `counts above a thousand are grouped`() {
        val status = SyncStatus(checkedImages = 4275, totalImages = 21790)
        assertEquals("Checking images (4,275/21,790)", imageStatusText(status))
    }

    @Test
    fun `a cache-hit only pass never claims a download`() {
        // The common case: every URL was already on disk, nothing was fetched.
        val status = SyncStatus(checkedImages = 4275, fetchedImages = 0, totalImages = 21790)
        assertEquals("Checking images (4,275/21,790)", imageStatusText(status))
    }

    @Test
    fun `real fetches are reported separately from the walk`() {
        val status = SyncStatus(checkedImages = 4275, fetchedImages = 312, totalImages = 21790)
        assertEquals(
            "Checking images (4,275/21,790) - 312 downloaded",
            imageStatusText(status),
        )
    }

    @Test
    fun `the download count is grouped as well`() {
        val status = SyncStatus(checkedImages = 20000, fetchedImages = 6308, totalImages = 21790)
        assertEquals(
            "Checking images (20,000/21,790) - 6,308 downloaded",
            imageStatusText(status),
        )
    }
}
