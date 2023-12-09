package com.freshdigitable.yttt.feature.timetable.twitch

import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.feature.timetable.FetchStreamUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

internal class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (accountRepository.getTwitchToken() == null) {
            return
        }
        val me = twitchRepository.fetchMe() ?: return
        val streams = twitchRepository.fetchFollowedStreams()
        val following = twitchRepository.fetchAllFollowings(me.id)
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll()
        val users = streams.map { it.user.id } + schedules.flatten().map { it.broadcaster.id }
        twitchRepository.findUsersById(users)
    }
}
