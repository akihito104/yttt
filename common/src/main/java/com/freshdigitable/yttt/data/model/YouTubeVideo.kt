package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant

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
    val description: String
    val viewerCount: BigInteger?
    fun needsUpdate(current: Instant): Boolean

    fun isLiveStream(): Boolean = scheduledStartDateTime != null
    fun isNowOnAir(): Boolean = actualStartDateTime != null && actualEndDateTime == null
    fun isUpcoming(): Boolean = isLiveStream() && actualStartDateTime == null

    data class Id(override val value: String) : YouTubeId

    companion object {
        val YouTubeVideo.url: String get() = "https://youtube.com/watch?v=${id.value}"
        val YouTubeVideo.isArchived: Boolean
            get() = !isLiveStream() || actualEndDateTime != null // FIXME needs broadcastContent
    }
}
