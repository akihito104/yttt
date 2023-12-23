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
    useCases: IdBaseClassMap<FetchSubscriptionListSourceUseCase>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id = when (MainNavRoute.Subscription.Page.getValue(savedStateHandle)) {
        LivePlatform.YOUTUBE -> YouTubeId::class
        LivePlatform.TWITCH -> TwitchId::class
    }
    private val fetchSubscriptionListSource = checkNotNull(useCases[id.java])

    fun getSubscriptionSource(): Flow<List<LiveSubscription>> = fetchSubscriptionListSource()
}
