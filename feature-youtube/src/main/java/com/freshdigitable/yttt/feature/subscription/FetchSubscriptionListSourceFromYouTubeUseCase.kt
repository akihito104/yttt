package com.freshdigitable.yttt.feature.subscription

import androidx.paging.PagingData
import com.freshdigitable.yttt.data.YouTubeRemoteMediator
import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class WatchLiveSubscriptionFromYouTubeUseCase @Inject constructor(
    private val remoteMediator: YouTubeRemoteMediator,
) : WatchSubscriptionPagingDataUseCase {
    override fun invoke(): Flow<PagingData<LiveSubscription>> = remoteMediator.page
}
