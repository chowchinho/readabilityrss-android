package co.chinho.readabilityreader.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDatabaseStats @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Room keeps the cached article HTML here; -wal/-shm hold uncheckpointed pages.
    fun databaseBytes(): Long {
        return DB_FILES.sumOf { name ->
            runCatching { context.getDatabasePath(name).takeIf(File::exists)?.length() ?: 0L }
                .getOrDefault(0L)
        }
    }

    private companion object {
        val DB_FILES = listOf("readability.db", "readability.db-wal", "readability.db-shm")
    }
}
