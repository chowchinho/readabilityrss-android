package co.chinho.readabilityreader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryImplTest {

    @Test
    fun `sync interval defaults to 1 hour`() = runTest {
        val repository = createRepository()

        assertEquals(1, repository.syncIntervalHours.first())
    }

    @Test
    fun `sync on app open defaults to true`() = runTest {
        val repository = createRepository()

        assertTrue(repository.syncOnAppOpen.first())
    }

    @Test
    fun `wifi only sync defaults to true`() = runTest {
        val repository = createRepository()

        assertTrue(repository.wifiOnlySync.first())
    }

    @Test
    fun `charging only sync defaults to false`() = runTest {
        val repository = createRepository()

        assertFalse(repository.chargingOnlySync.first())
    }

    @Test
    fun `auto mark delay defaults to 2 seconds`() = runTest {
        val repository = createRepository()

        assertEquals(2, repository.autoMarkReadDelaySec.first())
    }

    @Test
    fun `cache duration defaults to 7 days`() = runTest {
        val repository = createRepository()

        assertEquals(7, repository.cacheDurationDays.first())
    }

    @Test
    fun `prefetch count defaults to 2`() = runTest {
        val repository = createRepository()

        assertEquals(2, repository.prefetchCount.first())
    }

    @Test
    fun `show read articles defaults to true`() = runTest {
        val repository = createRepository()

        assertTrue(repository.showReadArticles.first())
    }

    @Test
    fun `external link handler defaults to system browser`() = runTest {
        val repository = createRepository()

        assertEquals("system_browser", repository.externalLinkHandler.first())
    }

    @Test
    fun `theme mode defaults to system`() = runTest {
        val repository = createRepository()

        assertEquals("system", repository.themeMode.first())
    }

    @Test
    fun `force offline defaults to false`() = runTest {
        val repository = createRepository()

        assertFalse(repository.forceOffline.first())
    }

    @Test
    fun `tablet pane preferences use orientation defaults`() = runTest {
        val repository = createRepository()

        assertEquals(0.33f, repository.tabletArticleListWidthLandscape.first(), 0.0001f)
        assertEquals(0.33f, repository.tabletArticleListWidthPortrait.first(), 0.0001f)
        assertFalse(repository.tabletFeedPaneCollapsedLandscape.first())
        assertTrue(repository.tabletFeedPaneCollapsedPortrait.first())
    }

    @Test
    fun `set sync interval persists new value`() = runTest {
        val repository = createRepository()

        repository.setSyncIntervalHours(6)

        assertEquals(6, repository.syncIntervalHours.first())
    }

    @Test
    fun `set prefetch count persists new value`() = runTest {
        val repository = createRepository()

        repository.setPrefetchCount(5)

        assertEquals(5, repository.prefetchCount.first())
    }

    @Test
    fun `set external link handler persists new value`() = runTest {
        val repository = createRepository()

        repository.setExternalLinkHandler("custom_tabs")

        assertEquals("custom_tabs", repository.externalLinkHandler.first())
    }

    @Test
    fun `set force offline persists new value`() = runTest {
        val repository = createRepository()

        repository.setForceOffline(true)

        assertTrue(repository.forceOffline.first())
    }

    @Test
    fun `tablet pane preferences round trip`() = runTest {
        val repository = createRepository()

        repository.setTabletArticleListWidthLandscape(0.42f)
        repository.setTabletArticleListWidthPortrait(0.29f)
        repository.setTabletFeedPaneCollapsedLandscape(true)
        repository.setTabletFeedPaneCollapsedPortrait(false)

        assertEquals(0.42f, repository.tabletArticleListWidthLandscape.first(), 0.0001f)
        assertEquals(0.29f, repository.tabletArticleListWidthPortrait.first(), 0.0001f)
        assertTrue(repository.tabletFeedPaneCollapsedLandscape.first())
        assertFalse(repository.tabletFeedPaneCollapsedPortrait.first())
    }

    private fun TestScope.createRepository(): UserPreferencesRepositoryImpl {
        val tempFile = File.createTempFile("user_preferences_test", ".preferences_pb").apply {
            deleteOnExit()
        }
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFile }
        )
        return UserPreferencesRepositoryImpl(dataStore)
    }
}
