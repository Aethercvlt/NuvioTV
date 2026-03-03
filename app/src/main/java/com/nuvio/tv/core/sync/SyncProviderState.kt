package com.nuvio.tv.core.sync

import com.nuvio.tv.data.local.SimklAuthDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.SyncSettingsDataStore
import com.nuvio.tv.domain.model.SyncProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@Singleton
class SyncProviderState @Inject constructor(
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val traktAuthDataStore: TraktAuthDataStore,
    private val simklAuthDataStore: SimklAuthDataStore
) {

    val selectedProvider: Flow<SyncProviderType> = syncSettingsDataStore.selectedProvider

    val traktIsActive: Flow<Boolean> = combine(
        selectedProvider,
        traktAuthDataStore.isEffectivelyAuthenticated
    ) { provider, isAuthenticated ->
        provider == SyncProviderType.TRAKT && isAuthenticated
    }.distinctUntilChanged()

    val simklIsActive: Flow<Boolean> = combine(
        selectedProvider,
        simklAuthDataStore.isEffectivelyAuthenticated
    ) { provider, isAuthenticated ->
        provider == SyncProviderType.SIMKL && isAuthenticated
    }.distinctUntilChanged()

    suspend fun isTraktActive(): Boolean = traktIsActive.first()

    suspend fun isSimklActive(): Boolean = simklIsActive.first()
}
