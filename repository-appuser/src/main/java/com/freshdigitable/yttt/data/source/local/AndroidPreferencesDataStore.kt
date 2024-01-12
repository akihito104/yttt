package com.freshdigitable.yttt.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
