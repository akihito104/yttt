package com.freshdigitable.yttt.feature.subscription

import androidx.paging.PagingData
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchRemoteMediator
import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

internal class WatchSubscriptionPagingDataFromTwitchUseCase @Inject constructor(
    private val source: TwitchRemoteMediator,
    private val accountRepository: TwitchAccountRepository,
) : WatchSubscriptionPagingDataUseCase {
    override fun invoke(): Flow<PagingData<LiveSubscription>> {
        if (accountRepository.getTwitchToken() == null) {
            return emptyFlow()
        }
        return source.pager
    }
}
