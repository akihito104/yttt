package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchRepository
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

internal class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchRepository,
    private val accountRepository: TwitchAccountRepository,
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
        val followings = twitchRepository.fetchAllFollowings(me.id)
        val following = followings.followings
        t.putMetric("subs", following.size.toLong())
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll().filterNotNull()
        t.putMetric("schedule_tasks", tasks.size.toLong())
        t.putMetric("schedule", schedules.size.toLong())

        val categoryId = schedules
            .flatMap { s -> s.segments?.mapNotNull { it.category?.id } ?: emptyList() }
        fetchCategoryArt(categoryId)

        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
        if (followings is TwitchFollowings.Updated) {
            twitchRepository.cleanUpByUserId(followings.removed)
        }
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

    private suspend fun fetchCategoryArt(id: Collection<TwitchCategory.Id>) {
        twitchRepository.fetchCategory(id.toSet())
    }
}
