package com.nuvio.tv.domain.model

enum class SyncProviderType {
    TRAKT,
    SIMKL;

    companion object {
        fun fromRaw(value: String?): SyncProviderType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TRAKT
        }
    }

    val displayName: String
        get() = when (this) {
            TRAKT -> "Trakt"
            SIMKL -> "Simkl"
        }
}
