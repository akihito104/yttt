package com.freshdigitable.yttt.data.model

import java.time.Instant
import kotlin.reflect.KClass

interface LiveSubscription : Comparable<LiveSubscription> {
    val id: Id
    val subscribeSince: Instant
    val channel: LiveChannel
    val order: Int

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId

    override fun compareTo(other: LiveSubscription): Int = order - other.order
}
