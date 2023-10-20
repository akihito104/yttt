package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    repository: YouTubeLiveRepository,
    twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    val subscriptions: LiveData<List<LiveSubscription>> =
        repository.subscriptions.asLiveData(viewModelScope.coroutineContext)
    val twitchSubs: LiveData<List<LiveSubscription>> = liveData {
        val me = twitchRepository.fetchMe()
        if (me == null) {
            emit(emptyList())
            return@liveData
        }
        val followings = twitchRepository.fetchAllFollowings(me.id).mapIndexed { i, f ->
            val u = twitchRepository.findUsersById(listOf(f.id)).first()
            f.toLiveSubscription(i, u)
        }
        emit(followings)
    }
}

fun TwitchBroadcaster.toLiveSubscription(order: Int, user: TwitchUserDetail): LiveSubscription {
    return LiveSubscriptionEntity(
        id = LiveSubscription.Id(id.value, id.platform),
        channel = user.toLiveChannel(),
        order = order,
        subscribeSince = followedAt,
    )
}
