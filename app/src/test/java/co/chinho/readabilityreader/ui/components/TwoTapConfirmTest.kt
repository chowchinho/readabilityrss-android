package co.chinho.readabilityreader.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoTapConfirmTest {

    @Test
    fun oneTapArmsAndDoesNotCallOnConfirm() {
        var callCount = 0
        val state = TwoTapConfirmState { callCount++ }

        assertFalse(state.armed)
        state.tap()

        assertTrue(state.armed)
        assertEquals(0, callCount)
    }

    @Test
    fun twoTapsCallOnConfirmExactlyOnceAndLeaveDisarmed() {
        var callCount = 0
        val state = TwoTapConfirmState { callCount++ }

        state.tap()
        state.tap()

        assertFalse(state.armed)
        assertEquals(1, callCount)
    }

    @Test
    fun armingThenAdvancingPastTimeoutDisarmsWithoutCallingOnConfirm() = runTest {
        var callCount = 0
        val state = TwoTapConfirmState { callCount++ }

        state.tap()
        assertTrue(state.armed)

        val timeoutJob = launch {
            delay(TwoTapConfirmTimeoutMillis)
            state.armed = false
        }

        advanceTimeBy(TwoTapConfirmTimeoutMillis / 2)
        assertTrue(state.armed)

        advanceTimeBy(TwoTapConfirmTimeoutMillis / 2 + 1)
        assertFalse(state.armed)
        assertEquals(0, callCount)

        timeoutJob.cancel()
    }

    @Test
    fun afterTimeoutDisarmSingleTapArmsAgainRatherThanFiring() = runTest {
        var callCount = 0
        val state = TwoTapConfirmState { callCount++ }

        state.tap()
        val timeoutJob = launch {
            delay(TwoTapConfirmTimeoutMillis)
            state.armed = false
        }
        advanceTimeBy(TwoTapConfirmTimeoutMillis + 10)
        assertFalse(state.armed)

        state.tap()
        assertTrue(state.armed)
        assertEquals(0, callCount)

        timeoutJob.cancel()
    }

    @Test
    fun onConfirmIsReadFreshAtTapTime() {
        var firstRan = false
        var secondRan = false
        var activeOnConfirm: () -> Unit = { firstRan = true }

        val state = TwoTapConfirmState { activeOnConfirm() }

        state.tap()
        assertTrue(state.armed)

        // Simulate recomposition swapping onConfirm lambda
        activeOnConfirm = { secondRan = true }

        state.tap()
        assertFalse(state.armed)
        assertFalse(firstRan)
        assertTrue(secondRan)
    }
}
