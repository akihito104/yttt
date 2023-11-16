package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.toLiveSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface FetchSubscriptionListSourceUseCase {
    operator fun invoke(): Flow<List<LiveSubscription>>
}

class FetchSubscriptionListSourceFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = repository.subscriptions
        .map { i -> i.map { it.toLiveSubscription() } }
}

class FetchSubscriptionListSourceFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = flow {
        val me = repository.fetchMe()
        if (me == null) {
            emit(emptyList())
            return@flow
        }
        val broadcasters = repository.fetchAllFollowings(me.id)
        val userIds = broadcasters.map { it.id }
        val users = repository.findUsersById(userIds).associateBy { it.id }
        val followings = broadcasters.mapIndexed { i, b ->
            val u = checkNotNull(users[b.id])
            b.toLiveSubscription(i, u)
        }
        emit(followings)
    }
}
