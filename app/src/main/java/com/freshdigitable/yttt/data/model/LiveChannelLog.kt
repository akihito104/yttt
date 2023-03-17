package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: LiveVideo.Id
    val channelId: LiveChannel.Id

    data class Id(val value: String)
}

data class LiveChannelLogEntity(
    override val id: LiveChannelLog.Id,
    override val dateTime: Instant,
    override val videoId: LiveVideo.Id,
    override val channelId: LiveChannel.Id,
) : LiveChannelLog
