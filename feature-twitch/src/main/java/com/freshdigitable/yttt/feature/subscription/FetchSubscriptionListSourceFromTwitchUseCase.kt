package com.freshdigitable.yttt.feature.subscription

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.toLiveSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            b.toLiveSubscription(i, u)
        }
        emit(followings)
    }
}
