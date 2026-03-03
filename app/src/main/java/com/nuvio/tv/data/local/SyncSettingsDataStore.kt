package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.SyncProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {

    companion object {
        private const val FEATURE = "sync_settings"
    }

    private val selectedProviderKey = stringPreferencesKey("selected_provider")

    val selectedProvider: Flow<SyncProviderType> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            SyncProviderType.fromRaw(prefs[selectedProviderKey])
        }
    }

    suspend fun setSelectedProvider(provider: SyncProviderType) {
        factory.get(profileManager.activeProfileId.value, FEATURE).edit { prefs ->
            prefs[selectedProviderKey] = provider.name
        }
    }
}
