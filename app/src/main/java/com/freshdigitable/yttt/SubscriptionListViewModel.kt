package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.toLiveSubscription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    repository: YouTubeRepository,
    twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    val subscriptions: LiveData<List<LiveSubscription>> = repository.subscriptions
        .map { i -> i.map { it.toLiveSubscription() } }
        .asLiveData(viewModelScope.coroutineContext)
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
