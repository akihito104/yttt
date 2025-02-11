package com.freshdigitable.yttt.feature.subscription

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

internal class FetchSubscriptionListSourceFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = flow {
        val subs = repository.fetchAllSubscribe().map { LiveSubscriptionYouTube(it) }
        emit(subs)
    }
}

internal data class LiveSubscriptionYouTube(
    private val subscriptions: YouTubeSubscription,
) : LiveSubscription {
    override val id: LiveSubscription.Id
        get() = subscriptions.id.mapTo()
    override val subscribeSince: Instant
        get() = subscriptions.subscribeSince
    override val channel: LiveChannel
        get() = subscriptions.channel.toLiveChannel()
    override val order: Int
        get() = subscriptions.order
}
