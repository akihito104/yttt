package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: YouTubeVideo.Id
    val channelId: YouTubeChannel.Id
    val thumbnailUrl: String
    val title: String

    data class Id(override val value: String) : YouTubeId
}

data class YouTubeChannelLogEntity(
    override val id: YouTubeChannelLog.Id,
    override val dateTime: Instant,
    override val videoId: YouTubeVideo.Id,
    override val channelId: YouTubeChannel.Id,
    override val thumbnailUrl: String,
    override val title: String,
) : YouTubeChannelLog
