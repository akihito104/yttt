package com.freshdigitable.yttt.feature.subscription

import androidx.paging.PagingData
import com.freshdigitable.yttt.data.TwitchRemoteMediator
import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class WatchSubscriptionPagingDataFromTwitchUseCase @Inject constructor(
    private val source: TwitchRemoteMediator,
) : WatchSubscriptionPagingDataUseCase {
    override fun invoke(): Flow<PagingData<LiveSubscription>> = source.pager
}
