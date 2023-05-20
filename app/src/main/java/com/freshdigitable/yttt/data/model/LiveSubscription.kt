package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveSubscription : Comparable<LiveSubscription> {
    val id: Id
    val subscribeSince: Instant
    val channel: LiveChannel
    val order: Int

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(override val value: String, override val platform: LivePlatform) : IdBase<String>

    override fun compareTo(other: LiveSubscription): Int = order - other.order
}

data class LiveSubscriptionEntity(
    override val id: LiveSubscription.Id,
    override val subscribeSince: Instant,
    override val channel: LiveChannel,
    override val order: Int,
) : LiveSubscription
