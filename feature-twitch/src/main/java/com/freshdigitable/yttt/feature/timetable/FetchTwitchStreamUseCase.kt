package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

internal class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
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
        val following = twitchRepository.fetchAllFollowings(me.id).followings
        t.putMetric("subs", following.size.toLong())
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll().flatten()
        t.putMetric("schedule_tasks", tasks.size.toLong())
        t.putMetric("schedule", schedules.size.toLong())
        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
        t.stop()
        logI { "end" }
    }

    private suspend fun updateOnAirStreams(me: TwitchUserDetail): List<TwitchStream> {
        val old = twitchRepository.onAir.value.associateBy { it.id }
        val new = twitchRepository.fetchFollowedStreams(me.id)
        val url = new.filter { n ->
            val o = old[n.id] ?: return@filter true
            n.mayUpdateThumbnail(o)
        }.map { it.getThumbnailUrl() }
        if (url.isNotEmpty()) {
            twitchRepository.removeImageByUrl(url)
        }
        twitchRepository.addFollowedStreams(new)
        return new
    }

    companion object {
        private fun TwitchStream.mayUpdateThumbnail(other: TwitchStream): Boolean =
            other.startedAt != startedAt || other.title != title || other.gameId != gameId
    }
}
