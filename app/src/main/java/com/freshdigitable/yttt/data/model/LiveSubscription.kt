package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveSubscription {
    val id: Id
    val subscribeSince: Instant
    val channelId: LiveChannel.Id

    data class Id(val value: String)
}
