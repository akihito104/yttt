package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow

interface TwitchAccountDataStore {
    val twitchToken: Flow<String?>
    fun getTwitchToken(): String?
    suspend fun putTwitchToken(token: String)
    suspend fun clearTwitchToken()
    val isTwitchTokenInvalidated: Flow<Boolean?>
    fun isTwitchTokenInvalidated(): Boolean
    suspend fun invalidateTwitchToken()
    suspend fun clearTwitchTokenInvalidated()
    val twitchOauthState: Flow<String?>
    suspend fun putTwitchOauthState(value: String)
    suspend fun clearTwitchOauthState()
    val twitchOauthStatus: Flow<TwitchOauthStatus?>
    suspend fun putTwitchOauthStatus(value: TwitchOauthStatus)
    suspend fun clearTwitchOauthStatus()

    interface Local : TwitchAccountDataStore
}

enum class TwitchOauthStatus {
    REQUESTED, SUCCEEDED,
    ;

    companion object {
        fun findByName(status: String?): TwitchOauthStatus? =
            if (status == null) null else entries.firstOrNull { it.name == status }
    }
}
