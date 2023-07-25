package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

@Singleton
class AndroidPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val googleAccountName: Flow<String?> = dataStore.data
        .map { it[DS_ACCOUNT_NAME] }

    suspend fun putAccount(account: String) {
        dataStore.edit {
            it[DS_ACCOUNT_NAME] = account
        }
    }

    val twitchToken: Flow<String?> = dataStore.data
        .map { it[DS_TWITCH_TOKEN] }

    suspend fun putTwitchToken(token: String) {
        dataStore.edit {
            it[DS_TWITCH_TOKEN] = token
        }
    }

    val changeDateTime: Flow<Int?> = dataStore.data.map { it[DS_CHANGE_DATE] }

    suspend fun putTimeToChangeDate(value: Int) {
        dataStore.edit {
            it[DS_CHANGE_DATE] = value
        }
    }

    companion object {
        private val DS_ACCOUNT_NAME = stringPreferencesKey("accountName")
        private val DS_TWITCH_TOKEN = stringPreferencesKey("twitchToken")
        private val DS_CHANGE_DATE = intPreferencesKey("timeToChangeDate")
    }
}
