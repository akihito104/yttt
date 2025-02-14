package com.freshdigitable.yttt.feature.subscription

import androidx.paging.PagingData
import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow

interface WatchSubscriptionPagingDataUseCase {
    operator fun invoke(): Flow<PagingData<LiveSubscription>>
}
