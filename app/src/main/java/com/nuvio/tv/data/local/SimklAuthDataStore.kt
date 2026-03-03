package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nuvio.tv.core.profile.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.simklAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "simkl_auth_store"
)

data class SimklAuthState(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val createdAt: Long? = null,
    val expiresIn: Int? = null,
    val username: String? = null,
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUrl: String? = null,
    val expiresAt: Long? = null,
    val pollInterval: Int? = null
) {
    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank()
}

@Singleton
class SimklAuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager
) {

    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val tokenTypeKey = stringPreferencesKey("token_type")
    private val createdAtKey = longPreferencesKey("created_at")
    private val expiresInKey = intPreferencesKey("expires_in")
    private val usernameKey = stringPreferencesKey("username")

    private val deviceCodeKey = stringPreferencesKey("device_code")
    private val userCodeKey = stringPreferencesKey("user_code")
    private val verificationUrlKey = stringPreferencesKey("verification_url")
    private val expiresAtKey = longPreferencesKey("expires_at")
    private val pollIntervalKey = intPreferencesKey("poll_interval")

    val state: Flow<SimklAuthState> = context.simklAuthDataStore.data.map { prefs ->
        SimklAuthState(
            accessToken = prefs[accessTokenKey],
            refreshToken = prefs[refreshTokenKey],
            tokenType = prefs[tokenTypeKey],
            createdAt = prefs[createdAtKey],
            expiresIn = prefs[expiresInKey],
            username = prefs[usernameKey],
            deviceCode = prefs[deviceCodeKey],
            userCode = prefs[userCodeKey],
            verificationUrl = prefs[verificationUrlKey],
            expiresAt = prefs[expiresAtKey],
            pollInterval = prefs[pollIntervalKey]
        )
    }

    val isAuthenticated: Flow<Boolean> = state.map { it.isAuthenticated }

    val isEffectivelyAuthenticated: Flow<Boolean> = combine(
        isAuthenticated,
        profileManager.activeProfileId
    ) { authenticated, profileId ->
        authenticated && profileId == 1
    }

    suspend fun saveToken(
        accessToken: String,
        refreshToken: String?,
        tokenType: String?,
        expiresIn: Int?,
        createdAt: Long = System.currentTimeMillis() / 1000L
    ) {
        context.simklAuthDataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            refreshToken?.let { prefs[refreshTokenKey] = it } ?: prefs.remove(refreshTokenKey)
            tokenType?.let { prefs[tokenTypeKey] = it } ?: prefs.remove(tokenTypeKey)
            expiresIn?.let { prefs[expiresInKey] = it } ?: prefs.remove(expiresInKey)
            prefs[createdAtKey] = createdAt
        }
    }

    suspend fun saveUser(username: String?) {
        context.simklAuthDataStore.edit { prefs ->
            if (username.isNullOrBlank()) {
                prefs.remove(usernameKey)
            } else {
                prefs[usernameKey] = username
            }
        }
    }

    suspend fun saveDeviceFlow(
        deviceCode: String,
        userCode: String,
        verificationUrl: String,
        expiresInSeconds: Int,
        pollIntervalSeconds: Int
    ) {
        val now = System.currentTimeMillis()
        context.simklAuthDataStore.edit { prefs ->
            prefs[deviceCodeKey] = deviceCode
            prefs[userCodeKey] = userCode
            prefs[verificationUrlKey] = verificationUrl
            prefs[expiresAtKey] = now + (expiresInSeconds * 1000L)
            prefs[pollIntervalKey] = pollIntervalSeconds
        }
    }

    suspend fun updatePollInterval(seconds: Int) {
        context.simklAuthDataStore.edit { prefs ->
            prefs[pollIntervalKey] = seconds
        }
    }

    suspend fun clearDeviceFlow() {
        context.simklAuthDataStore.edit { prefs ->
            prefs.remove(deviceCodeKey)
            prefs.remove(userCodeKey)
            prefs.remove(verificationUrlKey)
            prefs.remove(expiresAtKey)
            prefs.remove(pollIntervalKey)
        }
    }

    suspend fun clearAuth() {
        context.simklAuthDataStore.edit { prefs ->
            prefs.remove(accessTokenKey)
            prefs.remove(refreshTokenKey)
            prefs.remove(tokenTypeKey)
            prefs.remove(createdAtKey)
            prefs.remove(expiresInKey)
            prefs.remove(usernameKey)
            prefs.remove(deviceCodeKey)
            prefs.remove(userCodeKey)
            prefs.remove(verificationUrlKey)
            prefs.remove(expiresAtKey)
            prefs.remove(pollIntervalKey)
        }
    }
}
