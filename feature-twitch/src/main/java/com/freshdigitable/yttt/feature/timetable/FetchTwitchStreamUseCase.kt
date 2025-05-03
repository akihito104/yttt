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
    override suspend operator fun invoke(): Result<Unit> = trace("loadList_t") {
        if (!accountRepository.hasAccount()) {
            return Result.success(Unit)
        }
        val meRes = twitchRepository.fetchMe()
            .mapCatching { it ?: throw IllegalStateException("twitch: me was null.") }
            .onFailure { return Result.failure(it) }
        val me = checkNotNull(meRes.getOrNull())

        val streams = updateOnAirStreams(me)
            .onFailure { return Result.failure(it) }
            .onSuccess { putMetric("streaming_channel", it.size.toLong()) }
            .getOrDefault(emptyList())

        val schedules = updateChannelSchedules(me, this)
            .onFailure { return Result.failure(it) }
            .onSuccess { putMetric("schedule", it.size.toLong()) }
            .getOrDefault(emptyList())

        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
            .onFailure { return Result.failure(it) }
        return Result.success(Unit)
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
        val followings = twitchRepository.fetchAllFollowings(me.id)
            .onFailure { return Result.failure(it) }
            .onSuccess {
                if (it is TwitchFollowings.Updated) {
                    twitchRepository.cleanUpByUserId(it.removed)
                }
            }.map { it.followings }
            .getOrDefault(emptyList())
        t.putMetric("subs", followings.size.toLong())
        if (followings.isEmpty()) {
            return Result.success(emptyList())
        }
        val schedules = fetchAllSchedule(followings)
            .onFailure { return Result.failure(it) }
            .getOrDefault(emptyList())

        val categoryId = schedules
            .flatMap { s -> s.segments?.mapNotNull { it.category?.id } ?: emptyList() }.toSet()
        if (categoryId.isNotEmpty()) {
            twitchRepository.fetchCategory(categoryId)
                .onFailure { logE(throwable = it) { "updateChannelSchedules: " } }
        }
        return Result.success(schedules)
    }

    private suspend fun fetchAllSchedule(following: List<TwitchBroadcaster>): Result<List<TwitchChannelSchedule>> {
        val tasks = coroutineScope {
            following.map { async { updateChannelSchedule(it) } }
        }
        val results = tasks.awaitAll()
        return if (results.all { it.isSuccess }) {
            Result.success(results.mapNotNull { it.getOrNull() })
        } else {
            Result.failure(results.first { it.isFailure }.exceptionOrNull()!!)
        }
    }

    private suspend fun updateChannelSchedule(it: TwitchBroadcaster): Result<TwitchChannelSchedule?> =
        twitchRepository.fetchFollowedStreamSchedule(it.id).onSuccess { s ->
            val segments = s.schedule?.segments ?: return@onSuccess
            val current = dateTimeProvider.now()
            val finished = segments.filter {
                (it.startTime + Duration.ofHours(6)) < current ||
                    (it.endTime != null && checkNotNull(it.endTime) < current)
            }.map { it.id }
            if (finished.isNotEmpty()) {
                twitchRepository.removeStreamScheduleById(finished.toSet())
            }
        }.onFailure { logE(throwable = it) { "updateChannelSchedule: " } }.map { it.schedule }

    companion object {
        private inline fun <T> trace(name: String, task: AppTrace.() -> T): T {
            val t = AppPerformance.newTrace(name)
            t.start()
            try {
                return task(t)
            } finally {
                t.stop()
            }
        }
    }
}
