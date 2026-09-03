package co.chinho.readabilityreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProgressFractionTest {

    private fun assertFraction(expected: Float, status: SyncStatus) {
        assertEquals(expected, syncProgressFraction(status), 0.0001f)
    }

    @Test
    fun `a lone active phase owns the whole bar`() {
        assertFraction(0.25f, SyncStatus(isSyncing = true, syncedCount = 25, totalCount = 100))
    }

    @Test
    fun `two phases combine by weight`() {
        // 1.0 * 0.30 + 0.5 * 0.70 = 0.30 + 0.35 = 0.65
        assertFraction(
            0.65f,
            SyncStatus(
                syncedCount = 100, totalCount = 100,
                checkedImages = 50, totalImages = 100,
            ),
        )
    }

    @Test
    fun `download phase moves the bar proportionally`() {
        // 0.5 * 0.30 + 0.5 * 0.70 = 0.15 + 0.35 = 0.50
        assertFraction(
            0.50f,
            SyncStatus(
                syncedCount = 50, totalCount = 100,
                checkedImages = 50, totalImages = 100,
            ),
        )
    }

    @Test
    fun `the bar does not sit full while image work is outstanding`() {
        val status = SyncStatus(
            hasCompletedSync = true,
            syncedCount = 100, totalCount = 100,
            checkedImages = 4275, totalImages = 21790,
        )
        assertTrue(syncProgressFraction(status) < 1f)
    }

    @Test
    fun `every phase complete reads as full`() {
        assertFraction(
            1f,
            SyncStatus(
                hasCompletedSync = true,
                syncedCount = 100, totalCount = 100,
                checkedImages = 100, totalImages = 100,
            ),
        )
    }

    @Test
    fun `a completed sync with no image work reads as full`() {
        assertFraction(1f, SyncStatus(hasCompletedSync = true))
    }

    @Test
    fun `preparing a sync reads as empty`() {
        assertFraction(0f, SyncStatus(isSyncing = true))
    }

    @Test
    fun `offline reads as empty`() {
        assertFraction(0f, SyncStatus(isOffline = true, hasCompletedSync = true))
    }
}
