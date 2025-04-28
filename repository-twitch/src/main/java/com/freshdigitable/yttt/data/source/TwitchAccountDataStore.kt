package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TwitchAccountDataStore {
    val twitchToken: StateFlow<String?>
    suspend fun putTwitchToken(token: String)
    suspend fun clearTwitchToken()
    val isTwitchTokenInvalidated: StateFlow<Boolean?>
    fun isTwitchTokenInvalidated(): Boolean = isTwitchTokenInvalidated.value ?: false
    suspend fun invalidateTwitchToken()
    suspend fun clearTwitchTokenInvalidated()

    interface Local : TwitchAccountDataStore
}

interface TwitchOauthDataStore : TwitchOauthRemoteDataStore, TwitchAccountDataStore {
    val twitchOauthState: Flow<String?>
    suspend fun putTwitchOauthState(value: String)
    suspend fun clearTwitchOauthState()
    val twitchOauthStatus: Flow<TwitchOauthStatus?>
    suspend fun putTwitchOauthStatus(value: TwitchOauthStatus)
    suspend fun clearTwitchOauthStatus()

    interface Local : TwitchOauthDataStore, TwitchAccountDataStore.Local {
        override suspend fun getAuthorizeUrl(state: String): Result<String> =
            throw NotImplementedError()
    }
}

interface TwitchOauthRemoteDataStore {
    suspend fun getAuthorizeUrl(state: String): Result<String>
}

enum class TwitchOauthStatus {
    REQUESTED, SUCCEEDED,
    ;

    companion object {
        fun findByName(status: String?): TwitchOauthStatus? =
            if (status == null) null else entries.firstOrNull { it.name == status }
    }
}
