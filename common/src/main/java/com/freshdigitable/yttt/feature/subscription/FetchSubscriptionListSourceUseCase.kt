package com.freshdigitable.yttt.feature.subscription

import com.freshdigitable.yttt.data.model.LiveSubscription
import kotlinx.coroutines.flow.Flow

interface FetchSubscriptionListSourceUseCase {
    operator fun invoke(): Flow<List<LiveSubscription>>
}
