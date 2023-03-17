package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveSubscription {
    val id: Id
    val subscribeSince: Instant
    val channelId: LiveChannel.Id

    data class Id(val value: String)
}

data class LiveSubscriptionEntity(
    override val id: LiveSubscription.Id,
    override val subscribeSince: Instant,
    override val channelId: LiveChannel.Id,
) : LiveSubscription
