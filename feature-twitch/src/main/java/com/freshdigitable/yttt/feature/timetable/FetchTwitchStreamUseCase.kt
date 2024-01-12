package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
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
        val me = twitchRepository.fetchMe() ?: return
        val streams = twitchRepository.fetchFollowedStreams()
        logI { "stream: ${streams.size}" }
        val following = twitchRepository.fetchAllFollowings(me.id)
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll().flatten()
        logI { "schedule: ${schedules.size}" }
        val users = streams.map { it.user.id } + schedules.map { it.broadcaster.id }
        twitchRepository.findUsersById(users.toSet())
        logI { "end" }
    }
}
