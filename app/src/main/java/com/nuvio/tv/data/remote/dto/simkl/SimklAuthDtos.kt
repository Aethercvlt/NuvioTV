package com.nuvio.tv.data.remote.dto.simkl

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SimklDeviceCodeResponseDto(
    @Json(name = "result") val result: String? = null,
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_url") val verificationUrl: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "interval") val interval: Int
)

@JsonClass(generateAdapter = true)
data class SimklCodeStatusResponseDto(
    @Json(name = "result") val result: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Int? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklUserSettingsResponseDto(
    @Json(name = "user") val user: SimklUserDto? = null,
    @Json(name = "account") val account: SimklAccountDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklUserDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "joined_at") val joinedAt: String? = null,
    @Json(name = "gender") val gender: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "bio") val bio: String? = null,
    @Json(name = "loc") val location: String? = null,
    @Json(name = "age") val age: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklAccountDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "type") val type: String? = null
)
