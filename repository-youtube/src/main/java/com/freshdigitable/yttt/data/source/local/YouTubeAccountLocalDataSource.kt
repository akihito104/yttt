package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
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
internal class YouTubeAccountLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : YouTubeAccountDataStore.Local {
    private val googleAccountName: StateFlow<String?> = dataStore.data
        .map { it[DS_ACCOUNT_NAME] }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    override val googleAccount: Flow<String?> = googleAccountName

    override fun getAccount(): String? = googleAccountName.value

    override suspend fun putAccount(account: String) {
        dataStore.edit {
            it[DS_ACCOUNT_NAME] = account
        }
    }

    companion object {
        private val DS_ACCOUNT_NAME = stringPreferencesKey("accountName")
    }
}
