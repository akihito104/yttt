package com.freshdigitable.yttt.feature.subscription

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

internal class FetchSubscriptionListSourceFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = flow {
        val me = repository.fetchMe()
        if (me == null) {
            emit(emptyList())
            return@flow
        }
        val broadcasters = repository.fetchAllFollowings(me.id).followings
        val userIds = broadcasters.map { it.id }.toSet()
        val users = repository.findUsersById(userIds).associateBy { it.id }
        val followings = broadcasters.mapIndexed { i, b ->
            val u = checkNotNull(users[b.id])
            LiveSubscriptionTwitch(b, u, i)
        }
        emit(followings)
    }
}

internal data class LiveSubscriptionTwitch(
    private val broadcaster: TwitchBroadcaster,
    private val user: TwitchUserDetail,
    override val order: Int,
) : LiveSubscription {
    override val id: LiveSubscription.Id
        get() = broadcaster.id.mapTo()
    override val subscribeSince: Instant
        get() = broadcaster.followedAt
    override val channel: LiveChannel
        get() = user.toLiveChannel()
}
