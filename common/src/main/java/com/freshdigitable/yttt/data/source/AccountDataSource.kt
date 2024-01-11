package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow

interface YouTubeAccountDataStore {
    val googleAccount: Flow<String?>
    fun getAccount(): String?
    suspend fun putAccount(account: String)
    fun hasAccount(): Boolean = getAccount() != null

    interface Local : YouTubeAccountDataStore
}

interface TwitchAccountDataStore {
    val twitchToken: Flow<String?>
    fun getTwitchToken(): String?
    suspend fun putTwitchToken(token: String)
    val twitchOauthState: Flow<String?>
    suspend fun putTwitchOauthState(value: String)
    suspend fun clearTwitchOauthState()
    val twitchOauthStatus: Flow<String?>
    suspend fun putTwitchOauthStatus(value: String)
    suspend fun clearTwitchOauthStatus()

    interface Local : TwitchAccountDataStore
}
