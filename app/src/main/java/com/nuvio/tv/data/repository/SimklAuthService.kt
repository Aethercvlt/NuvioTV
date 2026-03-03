package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.SimklAuthDataStore
import com.nuvio.tv.data.local.SimklAuthState
import com.nuvio.tv.data.remote.api.SimklApi
import com.nuvio.tv.data.remote.dto.simkl.SimklDeviceCodeResponseDto
import kotlinx.coroutines.flow.first
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SimklTokenPollResult {
    data object Pending : SimklTokenPollResult
    data class SlowDown(val pollIntervalSeconds: Int) : SimklTokenPollResult
    data object Expired : SimklTokenPollResult
    data class Approved(val username: String?) : SimklTokenPollResult
    data class Failed(val reason: String) : SimklTokenPollResult
}

@Singleton
class SimklAuthService @Inject constructor(
    private val simklApi: SimklApi,
    private val simklAuthDataStore: SimklAuthDataStore
) {

    fun hasRequiredCredentials(): Boolean {
        return BuildConfig.SIMKL_CLIENT_ID.isNotBlank()
    }

    suspend fun getCurrentAuthState(): SimklAuthState = simklAuthDataStore.state.first()

    suspend fun startDeviceAuth(): Result<SimklDeviceCodeResponseDto> {
        if (!hasRequiredCredentials()) {
            return Result.failure(IllegalStateException("Missing SIMKL_CLIENT_ID in local.properties"))
        }

        val response = runCatching {
            simklApi.requestDeviceCode(clientId = BuildConfig.SIMKL_CLIENT_ID)
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to request Simkl device code", throwable)
            return Result.failure(throwable)
        }

        val body = response.body()
        val isOk = body?.result?.equals("OK", ignoreCase = true) == true
        if (!response.isSuccessful || body == null || !isOk) {
            return Result.failure(
                IllegalStateException("Failed to start Simkl auth (${response.code()})")
            )
        }

        simklAuthDataStore.saveDeviceFlow(
            deviceCode = body.deviceCode,
            userCode = body.userCode,
            verificationUrl = body.verificationUrl,
            expiresInSeconds = body.expiresIn,
            pollIntervalSeconds = body.interval
        )
        return Result.success(body)
    }

    suspend fun pollDeviceToken(): SimklTokenPollResult {
        if (!hasRequiredCredentials()) {
            return SimklTokenPollResult.Failed("Missing SIMKL credentials")
        }

        val state = getCurrentAuthState()
        val userCode = state.userCode
        if (userCode.isNullOrBlank()) {
            return SimklTokenPollResult.Failed("No active Simkl device code")
        }

        val response = try {
            simklApi.getCodeStatus(userCode = userCode, clientId = BuildConfig.SIMKL_CLIENT_ID)
        } catch (e: IOException) {
            Log.w(TAG, "Network error while polling Simkl PIN", e)
            return SimklTokenPollResult.Failed("Network error while polling Simkl PIN")
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return SimklTokenPollResult.Failed("Simkl token polling failed (${response.code()})")
        }

        val normalizedResult = body.result?.lowercase()
        if (normalizedResult == "ok" && !body.accessToken.isNullOrBlank()) {
            simklAuthDataStore.saveToken(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                tokenType = body.tokenType,
                expiresIn = body.expiresIn
            )
            simklAuthDataStore.clearDeviceFlow()
            val username = fetchUserSettings()
            return SimklTokenPollResult.Approved(username)
        }

        val message = body.message?.lowercase()?.trim()
        return when {
            message == null || message.contains("pending") -> SimklTokenPollResult.Pending
            message.contains("slow") -> {
                val nextInterval = ((state.pollInterval ?: 5) + 5).coerceAtMost(60)
                simklAuthDataStore.updatePollInterval(nextInterval)
                SimklTokenPollResult.SlowDown(nextInterval)
            }
            message.contains("expired") -> {
                simklAuthDataStore.clearDeviceFlow()
                SimklTokenPollResult.Expired
            }
            else -> SimklTokenPollResult.Failed(body.message ?: "Unknown Simkl polling state")
        }
    }

    suspend fun revokeAndLogout() {
        simklAuthDataStore.clearAuth()
    }

    suspend fun fetchUserSettings(): String? {
        val response = executeAuthorizedRequest { authorization ->
            simklApi.getUserSettings(authorization)
        } ?: return null

        if (!response.isSuccessful) return null
        val username = response.body()?.user?.name
        simklAuthDataStore.saveUser(username)
        return username
    }

    suspend fun <T> executeAuthorizedRequest(
        call: suspend (authorizationHeader: String) -> Response<T>
    ): Response<T>? {
        val token = getCurrentAuthState().accessToken ?: return null
        val authHeader = "Bearer $token"
        return try {
            call(authHeader)
        } catch (e: IOException) {
            Log.w(TAG, "Simkl authorized request failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "SimklAuthService"
    }
}
