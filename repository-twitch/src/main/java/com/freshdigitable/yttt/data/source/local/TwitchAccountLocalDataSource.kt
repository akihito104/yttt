package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.freshdigitable.yttt.data.source.TwitchOauthDataStore
import com.freshdigitable.yttt.data.source.TwitchOauthStatus
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
internal class TwitchAccountLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : TwitchOauthDataStore.Local {
    override val twitchToken: StateFlow<String?> = dataStore.data
        .map { it[DS_TWITCH_TOKEN] }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override suspend fun putTwitchToken(token: String) {
        dataStore.edit {
            it[DS_TWITCH_TOKEN] = token
        }
    }

    override suspend fun clearTwitchToken() {
        dataStore.edit { it.remove(DS_TWITCH_TOKEN) }
    }

    override val isTwitchTokenInvalidated: StateFlow<Boolean?> = dataStore.data
        .map { it[DS_TWITCH_TOKEN_INVALIDATED] }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    override suspend fun invalidateTwitchToken() {
        dataStore.edit { it[DS_TWITCH_TOKEN_INVALIDATED] = true }
    }

    override suspend fun clearTwitchTokenInvalidated() {
        dataStore.edit { it.remove(DS_TWITCH_TOKEN_INVALIDATED) }
    }

    override val twitchOauthState: Flow<String?> = dataStore.data.map { it[DS_TWITCH_STATE] }
    override suspend fun putTwitchOauthState(value: String) {
        dataStore.edit { it[DS_TWITCH_STATE] = value }
    }

    override suspend fun clearTwitchOauthState() {
        dataStore.edit { it.remove(DS_TWITCH_STATE) }
    }

    override val twitchOauthStatus: Flow<TwitchOauthStatus?> = dataStore.data
        .map { it[DS_TWITCH_STATUS] }
        .map { TwitchOauthStatus.findByName(it) }

    override suspend fun putTwitchOauthStatus(value: TwitchOauthStatus) {
        dataStore.edit { it[DS_TWITCH_STATUS] = value.name }
    }

    override suspend fun clearTwitchOauthStatus() {
        dataStore.edit { it.remove(DS_TWITCH_STATUS) }
    }

    companion object {
        private val DS_TWITCH_TOKEN = stringPreferencesKey("twitchToken")
        private val DS_TWITCH_TOKEN_INVALIDATED = booleanPreferencesKey("twitchTokenInvalidated")
        private val DS_TWITCH_STATE = stringPreferencesKey("twitchOauthState")
        private val DS_TWITCH_STATUS = stringPreferencesKey("twitchOauthStatus")
    }
}
