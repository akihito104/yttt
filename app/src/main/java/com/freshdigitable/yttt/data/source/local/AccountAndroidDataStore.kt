package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.source.AccountLocalDataSource
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
internal class AccountAndroidDataStore @Inject constructor(
    private val dataStore: AndroidPreferencesDataStore,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : AccountLocalDataSource {
    private val _googleAccount: StateFlow<String?> = dataStore.googleAccountName
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    override val googleAccount: Flow<String?> = _googleAccount

    override fun getAccount(): String? = _googleAccount.value

    override suspend fun putAccount(account: String) {
        dataStore.putAccount(account)
    }

    private val twitchToken: StateFlow<String?> = dataStore.twitchToken
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override fun getTwitchToken(): String? = twitchToken.value

    override suspend fun putTwitchToken(token: String) {
        dataStore.putTwitchToken(token)
    }
}
