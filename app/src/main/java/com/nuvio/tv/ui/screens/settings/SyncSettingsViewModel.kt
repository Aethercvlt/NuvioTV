package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.SimklAuthDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.SyncSettingsDataStore
import com.nuvio.tv.domain.model.SyncProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncProviderConnectionState(
    val isConnected: Boolean = false,
    val username: String? = null
)

data class SyncSettingsUiState(
    val selectedProvider: SyncProviderType = SyncProviderType.TRAKT,
    val traktState: SyncProviderConnectionState = SyncProviderConnectionState(),
    val simklState: SyncProviderConnectionState = SyncProviderConnectionState()
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val syncSettingsDataStore: SyncSettingsDataStore,
    traktAuthDataStore: TraktAuthDataStore,
    simklAuthDataStore: SimklAuthDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncSettingsDataStore.selectedProvider,
                traktAuthDataStore.state,
                simklAuthDataStore.state
            ) { selected, traktState, simklState ->
                SyncSettingsUiState(
                    selectedProvider = selected,
                    traktState = SyncProviderConnectionState(
                        isConnected = traktState.isAuthenticated,
                        username = traktState.username
                    ),
                    simklState = SyncProviderConnectionState(
                        isConnected = simklState.isAuthenticated,
                        username = simklState.username
                    )
                )
            }.collect { newState ->
                _uiState.update { newState }
            }
        }
    }

    fun onProviderSelected(provider: SyncProviderType) {
        viewModelScope.launch {
            syncSettingsDataStore.setSelectedProvider(provider)
        }
    }
}
