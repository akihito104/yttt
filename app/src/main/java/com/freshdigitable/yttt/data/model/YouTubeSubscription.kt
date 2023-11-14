package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeSubscription {
    val id: Id
    val subscribeSince: Instant
    val channel: YouTubeChannel
    val order: Int

    data class Id(override val value: String) : YouTubeId
}

data class YouTubeSubscriptionEntity(
    override val id: YouTubeSubscription.Id,
    override val subscribeSince: Instant,
    override val channel: YouTubeChannel,
    override val order: Int,
) : YouTubeSubscription
