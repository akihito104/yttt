package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchScheduleDataSource
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import com.freshdigitable.yttt.feature.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

internal class FetchTwitchOnAirItemSourceUseCase @Inject constructor(
    private val dataSource: TwitchStreamDataSource.Extended,
) : FetchTimetableItemSourceUseCase {
    override operator fun invoke(): Flow<List<LiveVideo<*>>> = dataSource.onAir.map {
        it.map { s -> LiveVideo.create(s) }
    }
}

internal class FetchTwitchUpcomingItemSourceUseCase @Inject constructor(
    private val dataSource: TwitchScheduleDataSource.Extended,
    private val dateTimeProvider: DateTimeProvider,
) : FetchTimetableItemSourceUseCase {
    companion object {
        private val UPCOMING_LIMIT = Duration.ofDays(7L)
    }

    override fun invoke(): Flow<List<LiveVideo<*>>> = dataSource.upcoming.map { upcoming ->
        val week = dateTimeProvider.now() + UPCOMING_LIMIT
        upcoming.filter { it.schedule.startTime.isBefore(week) }.map { LiveVideo.create(it) }
    }
}
