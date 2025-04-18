package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeSubscription {
    val id: Id
    val subscribeSince: Instant
    val channel: YouTubeChannel
    val order: Int

    data class Id(override val value: String) : YouTubeId
}

interface YouTubeSubscriptions {
    val items: List<YouTubeSubscription>
    val lastUpdatedAt: Instant

    companion object {
        fun create(
            items: List<YouTubeSubscription>,
            lastUpdatedAt: Instant,
        ): YouTubeSubscriptions = Impl(items, lastUpdatedAt)
    }

    private class Impl(
        override val items: List<YouTubeSubscription>,
        override val lastUpdatedAt: Instant,
    ) : YouTubeSubscriptions

    interface Paged : YouTubeSubscriptions {
        val lastPage: List<YouTubeSubscription>
        val hasNextPage: Boolean
    }

    class Updated(
        private val cache: YouTubeSubscriptions,
        private val remote: Paged,
    ) : Paged by remote {
        val deleted: Set<YouTubeSubscription.Id>
            get() = cache.items.map { it.id }.toSet() - remote.items.map { it.id }.toSet()
    }
}
