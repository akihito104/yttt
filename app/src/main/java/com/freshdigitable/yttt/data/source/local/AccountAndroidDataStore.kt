package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.freshdigitable.yttt.data.source.AccountDataStore
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccountAndroidDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : AccountLocalDataSource {
    private val _googleAccount: StateFlow<String?> = dataStore.data
        .map { it[DS_ACCOUNT_NAME] }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    override val googleAccount: Flow<String?> = _googleAccount

    override fun getAccount(): String? = _googleAccount.value

    override suspend fun putAccount(account: String) {
        dataStore.edit {
            it[DS_ACCOUNT_NAME] = account
        }
    }

    private val twitchToken: StateFlow<String?> = dataStore.data
        .map { it[DS_TWITCH_TOKEN] }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override fun getTwitchToken(): String? = twitchToken.value

    override suspend fun putTwitchToken(token: String) {
        dataStore.edit {
            it[DS_TWITCH_TOKEN] = token
        }
    }

    companion object {
        private val DS_ACCOUNT_NAME = stringPreferencesKey(AccountDataStore.PREF_ACCOUNT_NAME)
        private val DS_TWITCH_TOKEN = stringPreferencesKey(AccountDataStore.PREF_TWITCH_TOKEN)
    }
}
