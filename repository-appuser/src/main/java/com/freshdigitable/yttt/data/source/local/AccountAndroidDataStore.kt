package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeAccountLocalDataSource @Inject constructor(
    private val dataStore: YouTubeAccountAndroidDataSource,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : YouTubeAccountDataStore.Local {
    private val _googleAccount: StateFlow<String?> = dataStore.googleAccountName
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    override val googleAccount: Flow<String?> = _googleAccount

    override fun getAccount(): String? = _googleAccount.value

    override suspend fun putAccount(account: String) {
        dataStore.putAccount(account)
    }
}

@Singleton
internal class TwitchAccountLocalDataSource @Inject constructor(
    private val dataStore: TwitchAccountAndroidDataSource,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : TwitchAccountDataStore.Local {
    override val twitchToken: StateFlow<String?> = dataStore.twitchToken
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override fun getTwitchToken(): String? = twitchToken.value

    override suspend fun putTwitchToken(token: String) {
        dataStore.putTwitchToken(token)
    }

    override val twitchOauthState: Flow<String?> = dataStore.twitchOauthState
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override suspend fun putTwitchOauthState(value: String) {
        dataStore.putTwitchOauthState(value)
    }

    override suspend fun clearTwitchOauthState() {
        dataStore.clearTwitchOauthState()
    }

    override val twitchOauthStatus: Flow<String?> = dataStore.twitchOauthStatus
    override suspend fun putTwitchOauthStatus(value: String) {
        dataStore.putTwitchOauthStatus(value)
    }

    override suspend fun clearTwitchOauthStatus() {
        dataStore.clearTwitchOauthStatus()
    }
}
