package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

internal class FetchTwitchOnAirItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override operator fun invoke(): Flow<List<LiveVideo<*>>> = repository.onAir.map {
        it.map { s -> LiveVideo.create(s) }
    }
}

internal class FetchTwitchUpcomingItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchTimetableItemSourceUseCase {
    companion object {
        private val UPCOMING_LIMIT = Duration.ofDays(7L)
    }

    override fun invoke(): Flow<List<LiveVideo<*>>> = repository.upcoming.map { upcoming ->
        val week = dateTimeProvider.now() + UPCOMING_LIMIT
        upcoming.filter { it.schedule.startTime.isBefore(week) }.map { LiveVideo.create(it) }
    }
}
