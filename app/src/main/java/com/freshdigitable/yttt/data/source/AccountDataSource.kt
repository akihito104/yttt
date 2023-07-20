package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow

interface AccountDataStore {
    val googleAccount: Flow<String?>
    fun getAccount(): String?
    suspend fun putAccount(account: String)
    fun getTwitchToken(): String?
    suspend fun putTwitchToken(token: String)

    companion object {
        internal const val PREF_ACCOUNT_NAME = "accountName"
        internal const val PREF_TWITCH_TOKEN = "twitchToken"
    }
}

interface AccountLocalDataSource : AccountDataStore
