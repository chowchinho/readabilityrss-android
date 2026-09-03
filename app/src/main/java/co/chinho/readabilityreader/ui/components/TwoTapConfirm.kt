package co.chinho.readabilityreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Stable
class TwoTapConfirmState internal constructor(
    private val onConfirm: () -> Unit,
) {
    var armed by mutableStateOf(false)
        internal set

    fun tap() {
        if (armed) {
            armed = false
            onConfirm()
        } else {
            armed = true
        }
    }
}

@Composable
fun rememberTwoTapConfirm(
    timeoutMillis: Long = TwoTapConfirmTimeoutMillis,
    onConfirm: () -> Unit,
): TwoTapConfirmState {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val state = remember { TwoTapConfirmState { currentOnConfirm() } }
    if (state.armed) {
        LaunchedEffect(state) {
            delay(timeoutMillis)
            state.armed = false
        }
    }
    return state
}

const val TwoTapConfirmTimeoutMillis = 3000L
