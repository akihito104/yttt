package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeSubscription {
    val id: Id
    val subscribeSince: Instant
    val channel: YouTubeChannel
    val order: Int

    data class Id(override val value: String) : YouTubeId
}
