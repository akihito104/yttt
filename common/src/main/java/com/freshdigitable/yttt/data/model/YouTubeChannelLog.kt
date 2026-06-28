package com.freshdigitable.yttt.data.model

import kotlinx.datetime.Instant

interface YouTubeChannelLog {
    val id: Id
    val dateTime: Instant
    val videoId: YouTubeVideo.Id?
    val channelId: YouTubeChannel.Id
    val thumbnailUrl: String
    val title: String
    val type: String

    @JvmInline
    value class Id(override val value: String) : YouTubeId
}
