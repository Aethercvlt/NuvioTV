package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.remote.dto.simkl.SimklCodeStatusResponseDto
import com.nuvio.tv.data.remote.dto.simkl.SimklDeviceCodeResponseDto
import com.nuvio.tv.data.remote.dto.simkl.SimklUserSettingsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SimklApi {

    @GET("oauth/pin")
    suspend fun requestDeviceCode(
        @Query("client_id") clientId: String,
        @Query("redirect") redirect: String? = null
    ): Response<SimklDeviceCodeResponseDto>

    @GET("oauth/pin/{userCode}")
    suspend fun getCodeStatus(
        @Path("userCode") userCode: String,
        @Query("client_id") clientId: String
    ): Response<SimklCodeStatusResponseDto>

    @POST("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") authorization: String
    ): Response<SimklUserSettingsResponseDto>
}
