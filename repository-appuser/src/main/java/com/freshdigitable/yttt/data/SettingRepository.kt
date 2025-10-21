package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import com.freshdigitable.yttt.feature.timetable.TimeAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepository @Inject constructor(
    private val preferences: AndroidPreferencesDataStore,
) {
    val changeDateTime: Flow<Int?> = preferences.changeDateTime
    val timeAdjustment: Flow<TimeAdjustment> = changeDateTime.map {
        TimeAdjustment(Duration.ofHours(((it ?: 24) - 24).toLong()))
    }

    suspend fun putTimeToChangeDate(date: Int) {
        preferences.putTimeToChangeDate(date)
    }

    var lastUpdateDatetime: Instant? = null

    val isInit: Flow<Boolean?> = preferences.isInit
    suspend fun putIsInit(isInit: Boolean) {
        preferences.putIsInit(isInit)
    }
}
