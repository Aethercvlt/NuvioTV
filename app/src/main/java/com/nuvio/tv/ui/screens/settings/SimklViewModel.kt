package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.SimklAuthDataStore
import com.nuvio.tv.data.local.SimklAuthState
import com.nuvio.tv.data.repository.SimklAuthService
import com.nuvio.tv.data.repository.SimklTokenPollResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SimklConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED
}

data class SimklUiState(
    val mode: SimklConnectionMode = SimklConnectionMode.DISCONNECTED,
    val credentialsConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val username: String? = null,
    val tokenExpiresAtMillis: Long? = null,
    val deviceUserCode: String? = null,
    val verificationUrl: String? = null,
    val pollIntervalSeconds: Int = 5,
    val deviceCodeExpiresAtMillis: Long? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SimklViewModel @Inject constructor(
    private val simklAuthService: SimklAuthService,
    private val simklAuthDataStore: SimklAuthDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SimklUiState())
    val uiState: StateFlow<SimklUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var lastMode: SimklConnectionMode = SimklConnectionMode.DISCONNECTED

    init {
        _uiState.update {
            it.copy(credentialsConfigured = simklAuthService.hasRequiredCredentials())
        }
        observeAuthState()
    }

    fun onConnectClick() {
        if (!simklAuthService.hasRequiredCredentials()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Missing SIMKL_CLIENT_ID in local.properties",
                    credentialsConfigured = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            val result = simklAuthService.startDeviceAuth()
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        isLoading = false,
                        statusMessage = "Enter code on simkl.com/pair"
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to start Simkl auth"
                    )
                }
            }
        }
    }

    fun onRetryPolling() {
        startPollingIfNeeded(force = true)
    }

    fun onCancelDeviceFlow() {
        viewModelScope.launch {
            pollJob?.cancel()
            simklAuthDataStore.clearDeviceFlow()
            _uiState.update {
                it.copy(
                    mode = SimklConnectionMode.DISCONNECTED,
                    isPolling = false,
                    statusMessage = null,
                    errorMessage = null
                )
            }
        }
    }

    fun onDisconnectClick() {
        viewModelScope.launch {
            pollJob?.cancel()
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            simklAuthService.revokeAndLogout()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    mode = SimklConnectionMode.DISCONNECTED,
                    isPolling = false,
                    statusMessage = "Disconnected from Simkl"
                )
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            simklAuthDataStore.state.collectLatest { authState ->
                applyAuthState(authState)
            }
        }
    }

    private fun applyAuthState(authState: SimklAuthState) {
        val expiresAtSeconds = (authState.createdAt ?: 0L) + (authState.expiresIn ?: 0)
        val tokenExpiresAtMillis = if (expiresAtSeconds > 0L) expiresAtSeconds * 1000L else null

        val mode = when {
            authState.isAuthenticated -> SimklConnectionMode.CONNECTED
            !authState.deviceCode.isNullOrBlank() -> SimklConnectionMode.AWAITING_APPROVAL
            else -> SimklConnectionMode.DISCONNECTED
        }

        _uiState.update { current ->
            current.copy(
                mode = mode,
                username = authState.username,
                tokenExpiresAtMillis = tokenExpiresAtMillis,
                deviceUserCode = authState.userCode,
                verificationUrl = authState.verificationUrl,
                pollIntervalSeconds = authState.pollInterval ?: 5,
                deviceCodeExpiresAtMillis = authState.expiresAt,
                credentialsConfigured = simklAuthService.hasRequiredCredentials(),
                isPolling = if (mode == SimklConnectionMode.CONNECTED) false else current.isPolling
            )
        }

        if (mode == SimklConnectionMode.CONNECTED && authState.username.isNullOrBlank()) {
            viewModelScope.launch { simklAuthService.fetchUserSettings() }
        }

        if (mode == SimklConnectionMode.AWAITING_APPROVAL) {
            startPollingIfNeeded(force = false)
        } else {
            pollJob?.cancel()
            pollJob = null
        }

        lastMode = mode
    }

    private fun startPollingIfNeeded(force: Boolean) {
        if (pollJob?.isActive == true && !force) return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true, errorMessage = null) }

            while (true) {
                val state = simklAuthService.getCurrentAuthState()
                val expiresAt = state.expiresAt
                if (expiresAt != null && System.currentTimeMillis() >= expiresAt) {
                    _uiState.update {
                        it.copy(
                            isPolling = false,
                            errorMessage = "Device code expired. Start again.",
                            statusMessage = null
                        )
                    }
                    simklAuthDataStore.clearDeviceFlow()
                    break
                }

                when (val poll = simklAuthService.pollDeviceToken()) {
                    SimklTokenPollResult.Pending -> {
                        _uiState.update {
                            it.copy(
                                isPolling = true,
                                statusMessage = "Waiting for approval..."
                            )
                        }
                    }

                    SimklTokenPollResult.Expired -> {
                        _uiState.update {
                            it.copy(
                                isPolling = false,
                                errorMessage = "Device code expired. Start again.",
                                statusMessage = null
                            )
                        }
                        break
                    }

                    is SimklTokenPollResult.SlowDown -> {
                        _uiState.update {
                            it.copy(
                                isPolling = true,
                                pollIntervalSeconds = poll.pollIntervalSeconds,
                                statusMessage = "Rate limited, slowing down polling..."
                            )
                        }
                    }

                    is SimklTokenPollResult.Approved -> {
                        _uiState.update {
                            it.copy(
                                isPolling = false,
                                statusMessage = "Connected as ${poll.username ?: "Simkl user"}",
                                errorMessage = null
                            )
                        }
                        break
                    }

                    is SimklTokenPollResult.Failed -> {
                        _uiState.update {
                            it.copy(
                                isPolling = false,
                                errorMessage = poll.reason,
                                statusMessage = null
                            )
                        }
                        break
                    }
                }

                val delaySeconds = (_uiState.value.pollIntervalSeconds).coerceAtLeast(1)
                delay(delaySeconds * 1000L)
            }
        }
    }
}
