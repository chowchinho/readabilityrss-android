package co.chinho.readabilityreader.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var syncScheduler: SyncScheduler

    @Before
    fun setup() {
        workManager = mockk(relaxed = true)
        syncScheduler = SyncScheduler(workManager)
    }

    @Test
    fun `triggerOneTimeSync enqueues unique work under ONE_TIME_SYNC_WORK_NAME with APPEND_OR_REPLACE`() {
        val uniqueNameSlot = slot<String>()
        val policySlot = slot<ExistingWorkPolicy>()
        val requestSlot = slot<OneTimeWorkRequest>()

        syncScheduler.triggerOneTimeSync()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                capture(uniqueNameSlot),
                capture(policySlot),
                capture(requestSlot),
            )
        }

        assertEquals(SyncScheduler.ONE_TIME_SYNC_WORK_NAME, uniqueNameSlot.captured)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policySlot.captured)
        assertTrue(requestSlot.captured.tags.contains(SyncScheduler.SYNC_WORK_TAG))
    }

    @Test
    fun `schedulePeriodicSync enqueues unique periodic work with UPDATE policy`() {
        val uniqueNameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        syncScheduler.schedulePeriodicSync(
            intervalHours = 6,
            wifiOnly = true,
            chargingOnly = false,
        )

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                capture(uniqueNameSlot),
                capture(policySlot),
                capture(requestSlot),
            )
        }

        assertEquals(SyncScheduler.PERIODIC_SYNC_WORK_NAME, uniqueNameSlot.captured)
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policySlot.captured)
        assertTrue(requestSlot.captured.tags.contains(SyncScheduler.PERIODIC_SYNC_WORK_TAG))
    }

    @Test
    fun `cancelPeriodicSync cancels unique periodic work`() {
        syncScheduler.cancelPeriodicSync()

        verify(exactly = 1) {
            workManager.cancelUniqueWork(SyncScheduler.PERIODIC_SYNC_WORK_NAME)
        }
    }
}
