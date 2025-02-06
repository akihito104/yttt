package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.toTwitchVideoList
import com.freshdigitable.yttt.feature.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

internal class FetchTwitchOnAirItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override operator fun invoke(): Flow<List<LiveVideo>> = repository.onAir.map {
        it.map { s ->
            val user = s.user as? TwitchUserDetail
                ?: repository.findUsersById(setOf(s.user.id)).first()
            LiveVideo.create(s, user)
        }
    }
}

internal class FetchTwitchUpcomingItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> = repository.upcoming.map { u ->
        val week = dateTimeProvider.now().plus(Duration.ofDays(7L))
        u.map { s -> s.toTwitchVideoList() }.flatten()
            .filter { it.schedule.startTime.isBefore(week) }
            .map { s ->
                val user = s.user as? TwitchUserDetail
                    ?: repository.findUsersById(setOf(s.user.id)).first()
                LiveVideo.create(s, user)
            }
    }
}
