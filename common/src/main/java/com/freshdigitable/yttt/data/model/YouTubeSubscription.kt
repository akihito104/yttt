package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.source.YouTubeDataSource
import java.time.Instant

interface YouTubeSubscription {
    val id: Id
    val subscribeSince: Instant
    val channel: YouTubeChannel

    data class Id(override val value: String) : YouTubeId
}

interface YouTubeSubscriptionRelevanceOrdered : YouTubeSubscription {
    val order: Int
}

interface YouTubeSubscriptionQuery {
    val offset: Int
    val nextPageToken: String?
    val eTag: String?
    val order: Order get() = Order.ALPHABETICAL
    val pageSize: Int get() = YouTubeDataSource.MAX_PAGE_SIZE

    enum class Order { ALPHABETICAL, RELEVANCE, }

    companion object {
        fun forRelevance(
            offset: Int,
            nextPageToken: String?,
            eTag: String? = null,
        ): YouTubeSubscriptionQuery = Impl(offset, nextPageToken, eTag, Order.RELEVANCE)

        fun forAlphabetical(
            offset: Int,
            nextPageToken: String?,
            eTag: String? = null,
        ): YouTubeSubscriptionQuery = Impl(offset, nextPageToken, eTag, Order.ALPHABETICAL)
    }

    private data class Impl(
        override val offset: Int,
        override val nextPageToken: String?,
        override val eTag: String?,
        override val order: Order,
    ) : YouTubeSubscriptionQuery
}
