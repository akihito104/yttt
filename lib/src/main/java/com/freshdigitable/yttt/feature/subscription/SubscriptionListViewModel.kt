package com.freshdigitable.yttt.feature.subscription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.freshdigitable.yttt.compose.MainNavRoute
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    useCases: ClassMap<LivePlatform, FetchSubscriptionListSourceUseCase>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val page = MainNavRoute.Subscription.getPlatform(savedStateHandle)
    private val fetchSubscriptionListSource = checkNotNull(useCases[page.java])

    fun getSubscriptionSource(): Flow<List<LiveSubscription>> = fetchSubscriptionListSource()
}
