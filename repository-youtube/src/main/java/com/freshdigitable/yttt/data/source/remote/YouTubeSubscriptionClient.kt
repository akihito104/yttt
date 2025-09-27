package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.text
import com.freshdigitable.yttt.data.source.remote.YouTubeClientImpl.Companion.fetch
import com.google.api.client.http.HttpHeaders
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.SubscriptionListResponse
import java.time.Instant

interface YouTubeSubscriptionClient {
    fun fetchSubscription(query: YouTubeSubscriptionQuery): NetworkResponse<List<YouTubeSubscription>>
}

internal class YouTubeSubscriptionClientImpl(private val youtube: YouTube) : YouTubeSubscriptionClient {
    override fun fetchSubscription(query: YouTubeSubscriptionQuery): NetworkResponse<List<YouTubeSubscription>> =
        youtube.fetch(YouTubeSubscriptionRemote.factory) {
            subscriptions()
                .list(listOf(YouTubeClient.Companion.PART_SNIPPET))
                .setMine(true)
                .setMaxResults(query.pageSize.toLong())
                .setPageToken(query.nextPageToken)
                .setOrder(query.order.text)
                .apply { query.eTag?.let { requestHeaders = HttpHeaders().setIfNoneMatch(it) } }
        }
}

private class YouTubeSubscriptionRemote(
    private val subscription: Subscription,
) : YouTubeSubscription {
    override val id: YouTubeSubscription.Id get() = YouTubeSubscription.Id(subscription.id)
    override val subscribeSince: Instant get() = Instant.ofEpochMilli(subscription.snippet.publishedAt.value)
    override val channel: YouTubeChannel
        get() = YouTubeChannelEntity(
            id = YouTubeChannel.Id(subscription.snippet.resourceId.channelId),
            iconUrl = subscription.snippet.thumbnails.iconUrl,
            title = subscription.snippet.title,
        )

    companion object {
        val factory: ResponseFactory<SubscriptionListResponse, List<YouTubeSubscription>> = { res, cc ->
            NetworkResponse.Companion.create(
                item = res.items.map { YouTubeSubscriptionRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
                eTag = res.etag,
            )
        }
    }
}
