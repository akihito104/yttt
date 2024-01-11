package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val changeDateTime: Flow<Int?> = dataStore.data.map { it[DS_CHANGE_DATE] }

    suspend fun putTimeToChangeDate(value: Int) {
        dataStore.edit {
            it[DS_CHANGE_DATE] = value
        }
    }

    val isInit: Flow<Boolean?> = dataStore.data.map { it[DS_IS_INIT] }
    suspend fun putIsInit(value: Boolean) {
        dataStore.edit {
            it[DS_IS_INIT] = value
        }
    }

    companion object {
        private val DS_CHANGE_DATE = intPreferencesKey("timeToChangeDate")
        private val DS_IS_INIT = booleanPreferencesKey("isInit")
    }
}

@Singleton
class YouTubeAccountAndroidDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val googleAccountName: Flow<String?> = dataStore.data
        .map { it[DS_ACCOUNT_NAME] }

    suspend fun putAccount(account: String) {
        dataStore.edit {
            it[DS_ACCOUNT_NAME] = account
        }
    }

    companion object {
        private val DS_ACCOUNT_NAME = stringPreferencesKey("accountName")
    }
}

@Singleton
class TwitchAccountAndroidDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val twitchToken: Flow<String?> = dataStore.data
        .map { it[DS_TWITCH_TOKEN] }

    suspend fun putTwitchToken(token: String) {
        dataStore.edit {
            it[DS_TWITCH_TOKEN] = token
        }
    }

    val twitchOauthState: Flow<String?> = dataStore.data.map { it[DS_TWITCH_STATE] }
    suspend fun putTwitchOauthState(value: String) {
        dataStore.edit { it[DS_TWITCH_STATE] = value }
    }

    suspend fun clearTwitchOauthState() {
        dataStore.edit { it.remove(DS_TWITCH_STATE) }
    }

    val twitchOauthStatus: Flow<String?> = dataStore.data.map { it[DS_TWITCH_STATUS] }
    suspend fun putTwitchOauthStatus(value: String) {
        dataStore.edit { it[DS_TWITCH_STATUS] = value }
    }

    suspend fun clearTwitchOauthStatus() {
        dataStore.edit { it.remove(DS_TWITCH_STATUS) }
    }

    companion object {
        private val DS_TWITCH_TOKEN = stringPreferencesKey("twitchToken")
        private val DS_TWITCH_STATE = stringPreferencesKey("twitchOauthState")
        private val DS_TWITCH_STATUS = stringPreferencesKey("twitchOauthStatus")
    }
}
