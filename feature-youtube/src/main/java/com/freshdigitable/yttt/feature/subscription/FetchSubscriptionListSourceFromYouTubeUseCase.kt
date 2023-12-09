package com.freshdigitable.yttt.feature.subscription

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.toLiveSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

internal class FetchSubscriptionListSourceFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = flow {
        val subs = repository.fetchAllSubscribe().map { it.toLiveSubscription() }
        emit(subs)
    }
}
