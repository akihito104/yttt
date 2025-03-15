package com.freshdigitable.yttt.feature.subscription

import androidx.paging.PagingData
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRemoteMediator
import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

internal class WatchLiveSubscriptionFromYouTubeUseCase @Inject constructor(
    private val remoteMediator: YouTubeRemoteMediator,
    private val accountRepository: YouTubeAccountRepository,
) : WatchSubscriptionPagingDataUseCase {
    override fun invoke(): Flow<PagingData<LiveSubscription>> {
        if (!accountRepository.hasAccount()) {
            return emptyFlow()
        }
        return remoteMediator.page
    }
}
