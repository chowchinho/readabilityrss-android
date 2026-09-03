package co.chinho.readabilityreader.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.chinho.readabilityreader.domain.usecase.SaveServerProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServerSetupUiState {
    data object Idle : ServerSetupUiState
    data object Loading : ServerSetupUiState
    data class Error(val message: String) : ServerSetupUiState
    data object Success : ServerSetupUiState
}

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val saveServerProfileUseCase: SaveServerProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ServerSetupUiState>(ServerSetupUiState.Idle)
    val uiState: StateFlow<ServerSetupUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent: SharedFlow<Unit> = _navigationEvent.asSharedFlow()

    fun saveProfile(serverUrl: String, username: String, password: String) {
        val trimmedUrl = serverUrl.trim()
        val trimmedUsername = username.trim()
        val trimmedPassword = password // Don't trim password as it might have intentional spaces

        if (trimmedUrl.isBlank() || trimmedUsername.isBlank() || trimmedPassword.isBlank()) {
            _uiState.value = ServerSetupUiState.Error("Please fill in all fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = ServerSetupUiState.Loading
            
            val result = saveServerProfileUseCase(
                name = "Primary",
                serverUrl = trimmedUrl,
                username = trimmedUsername,
                password = trimmedPassword
            )

            if (result.isSuccess) {
                _uiState.value = ServerSetupUiState.Success
                _navigationEvent.emit(Unit)
            } else {
                _uiState.value = ServerSetupUiState.Error(result.exceptionOrNull()?.message ?: "Failed to save profile")
            }
        }
    }
}
