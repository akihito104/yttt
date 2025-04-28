package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.TwitchRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.logE
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import javax.inject.Inject

internal class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchRepository,
    @LivePlatformQualifier(Twitch::class) private val accountRepository: AccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (!accountRepository.hasAccount()) {
            return
        }
        logI { "start" }
        val t = AppPerformance.newTrace("loadList_t")
        val me = twitchRepository.fetchMe().getOrNull() ?: return
        t.start()
        val streams = updateOnAirStreams(me).onSuccess {
            t.putMetric("streaming_channel", it.size.toLong())
        }.getOrDefault(emptyList())

        val schedules = updateChannelSchedules(me, t).onSuccess {
            t.putMetric("schedule", it.size.toLong())
        }.getOrDefault(emptyList())

        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
        t.stop()
        logI { "end" }
    }

    private suspend fun updateOnAirStreams(me: TwitchUserDetail): Result<List<TwitchStream>> =
        twitchRepository.fetchFollowedStreams(me.id).onSuccess {
            if (it is TwitchStreams.Updated) {
                val updatableThumbnails = it.updatableThumbnails
                if (updatableThumbnails.isNotEmpty()) {
                    twitchRepository.removeImageByUrl(updatableThumbnails)
                }
                twitchRepository.replaceFollowedStreams(it)
            }
        }.onFailure {
            logE(throwable = it) { "updateOnAirStreams: " }
        }.map { checkNotNull(it).streams }

    private suspend fun updateChannelSchedules(
        me: TwitchUserDetail,
        t: AppTrace,
    ): Result<List<TwitchChannelSchedule>> {
        val followingsRes = twitchRepository.fetchAllFollowings(me.id).onSuccess {
            if (it is TwitchFollowings.Updated) {
                twitchRepository.cleanUpByUserId(it.removed)
            }
        }.onFailure { logE(throwable = it) { "" } }
        if (followingsRes.isFailure) {
            return Result.failure(followingsRes.exceptionOrNull()!!)
        }
        val followings = checkNotNull(followingsRes.getOrNull()).followings
        t.putMetric("subs", followings.size.toLong())
        if (followings.isEmpty()) {
            return Result.success(emptyList())
        }
        val schedules = fetchAllSchedule(followings)

        val categoryId = schedules
            .flatMap { s -> s.segments?.mapNotNull { it.category?.id } ?: emptyList() }.toSet()
        if (categoryId.isNotEmpty()) {
            twitchRepository.fetchCategory(categoryId)
        }
        return Result.success(schedules)
    }

    private suspend fun fetchAllSchedule(following: List<TwitchBroadcaster>): List<TwitchChannelSchedule> {
        val tasks = coroutineScope {
            following.map { async { updateChannelSchedule(it) } }
        }
        return tasks.awaitAll()
            .map { res -> res.map { it }.getOrNull() }
            .mapNotNull { it }
    }

    private suspend fun updateChannelSchedule(it: TwitchBroadcaster): Result<TwitchChannelSchedule?> =
        twitchRepository.fetchFollowedStreamSchedule(it.id).onSuccess { s ->
            val segments = s?.segments ?: return@onSuccess
            val current = dateTimeProvider.now()
            val finished = segments.filter {
                (it.startTime + Duration.ofHours(6)) < current ||
                    (it.endTime != null && checkNotNull(it.endTime) < current)
            }.map { it.id }
            if (finished.isNotEmpty()) {
                twitchRepository.removeStreamScheduleById(finished.toSet())
            }
        }.onFailure { logE(throwable = it) { "updateChannelSchedule: " } }
}
