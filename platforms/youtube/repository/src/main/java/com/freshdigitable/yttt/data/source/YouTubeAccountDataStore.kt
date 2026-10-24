package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow

interface YouTubeAccountDataStore {
    val googleAccount: Flow<String?>
    fun getAccount(): String?
    suspend fun putAccount(account: String)
    fun hasAccount(): Boolean = getAccount() != null
    suspend fun clearAccount()

    interface Local : YouTubeAccountDataStore
}
