package com.freshdigitable.yttt.feature.subscription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.freshdigitable.yttt.compose.MainNavRoute
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val useCases: IdBaseClassMap<FetchSubscriptionListSourceUseCase>,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    fun getSubscriptionSource(): Flow<List<LiveSubscription>> {
        val id = when (MainNavRoute.Subscription.Page.getValue(savedStateHandle)) {
            LivePlatform.YOUTUBE -> YouTubeId::class
            LivePlatform.TWITCH -> TwitchId::class
        }
        val fetchSubscriptionListSource = checkNotNull(useCases[id.java])
        return fetchSubscriptionListSource()
    }
}
