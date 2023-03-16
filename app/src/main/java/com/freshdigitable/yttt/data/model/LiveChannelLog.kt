package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: LiveVideo.Id
    val channelId: LiveChannel.Id

    data class Id(val value: String)
}
