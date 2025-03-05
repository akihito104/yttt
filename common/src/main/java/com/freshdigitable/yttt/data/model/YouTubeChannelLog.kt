package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: YouTubeVideo.Id?
    val channelId: YouTubeChannel.Id
    val thumbnailUrl: String
    val title: String
    val type: String

    data class Id(override val value: String) : YouTubeId
}
