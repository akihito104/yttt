package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import javax.inject.Inject

internal class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchRepository,
    private val accountRepository: TwitchAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (accountRepository.getTwitchToken() == null) {
            return
        }
        logI { "start" }
        val t = AppPerformance.newTrace("loadList_t")
        val me = twitchRepository.fetchMe() ?: return
        t.start()
        val streams = updateOnAirStreams(me)
        t.putMetric("streaming_channel", streams.size.toLong())

        val schedules = updateChannelSchedules(me, t)
        t.putMetric("schedule", schedules.size.toLong())

        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
        t.stop()
        logI { "end" }
    }

    private suspend fun updateOnAirStreams(me: TwitchUserDetail): List<TwitchStream> {
        val new = checkNotNull(twitchRepository.fetchFollowedStreams(me.id))
        if (new is TwitchStreams.Updated) {
            val updatableThumbnails = new.updatableThumbnails
            if (updatableThumbnails.isNotEmpty()) {
                twitchRepository.removeImageByUrl(updatableThumbnails)
            }
            twitchRepository.replaceFollowedStreams(new)
        }
        return new.streams
    }

    private suspend fun updateChannelSchedules(
        me: TwitchUserDetail,
        t: AppTrace,
    ): List<TwitchChannelSchedule> {
        val followings = twitchRepository.fetchAllFollowings(me.id)
        if (followings is TwitchFollowings.Updated) {
            twitchRepository.cleanUpByUserId(followings.removed)
        }

        t.putMetric("subs", followings.followings.size.toLong())
        val schedules = fetchAllSchedule(followings.followings)

        val categoryId = schedules
            .flatMap { s -> s.segments?.mapNotNull { it.category?.id } ?: emptyList() }
        twitchRepository.fetchCategory(categoryId.toSet())
        return schedules
    }

    private suspend fun fetchAllSchedule(following: List<TwitchBroadcaster>): List<TwitchChannelSchedule> {
        val tasks = coroutineScope {
            following.map { async { updateChannelSchedule(it) } }
        }
        return tasks.awaitAll().filterNotNull()
    }

    private suspend fun updateChannelSchedule(it: TwitchBroadcaster): TwitchChannelSchedule? {
        val schedule = twitchRepository.fetchFollowedStreamSchedule(it.id)
        val segments = schedule?.segments ?: return schedule
        val current = dateTimeProvider.now()
        val finished = segments.filter {
            (it.startTime + Duration.ofHours(6)) < current ||
                (it.endTime != null && checkNotNull(it.endTime) < current)
        }.map { it.id }
        if (finished.isNotEmpty()) {
            twitchRepository.removeStreamScheduleById(finished.toSet())
        }
        return schedule
    }
}
