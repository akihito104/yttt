package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepository @Inject constructor(
    private val preferences: AndroidPreferencesDataStore,
) {
    val changeDateTime: Flow<Int?> = preferences.changeDateTime
    suspend fun putTimeToChangeDate(date: Int) {
        preferences.putTimeToChangeDate(date)
    }

    var lastUpdateDatetime: Instant? = null

    val isInit: Flow<Boolean?> = preferences.isInit
    suspend fun putIsInit(isInit: Boolean) {
        preferences.putIsInit(isInit)
    }
}
