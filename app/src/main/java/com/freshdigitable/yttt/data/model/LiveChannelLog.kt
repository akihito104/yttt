package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: LiveVideo.Id
    val channelId: LiveChannel.Id
    val thumbnailUrl: String

    data class Id(override val value: String) : IdBase<String> {
        override val platform: LivePlatform = LivePlatform.YOUTUBE
    }
}

data class LiveChannelLogEntity(
    override val id: LiveChannelLog.Id,
    override val dateTime: Instant,
    override val videoId: LiveVideo.Id,
    override val channelId: LiveChannel.Id,
    override val thumbnailUrl: String,
) : LiveChannelLog
