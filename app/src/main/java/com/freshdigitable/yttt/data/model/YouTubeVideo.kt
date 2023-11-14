package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant

interface YouTubeId : IdBase

interface YouTubeVideo {
    val id: Id
    val title: String
    val channel: YouTubeChannel
    val thumbnailUrl: String
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?
    val isFreeChat: Boolean? get() = null

    fun isLiveStream(): Boolean = scheduledStartDateTime != null
    fun isNowOnAir(): Boolean = actualStartDateTime != null && actualEndDateTime == null
    fun isUpcoming(): Boolean = isLiveStream() && actualStartDateTime == null

    data class Id(override val value: String) : YouTubeId

    companion object {
        val YouTubeVideo.url: String get() = "https://youtube.com/watch?v=${id.value}"
    }
}

data class YouTubeVideoEntity(
    override val id: YouTubeVideo.Id,
    override val channel: YouTubeChannel,
    override val title: String,
    override val scheduledStartDateTime: Instant?,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val thumbnailUrl: String,
) : YouTubeVideo

interface YouTubeVideoDetail : YouTubeVideo {
    val description: String
    val viewerCount: BigInteger?
}
