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
    val liveBroadcastContent: BroadcastType?
    fun needsUpdate(current: Instant): Boolean

    fun isLiveStream(): Boolean = isNowOnAir() || isUpcoming()
    fun isNowOnAir(): Boolean = liveBroadcastContent == BroadcastType.LIVE
    fun isUpcoming(): Boolean = liveBroadcastContent == BroadcastType.UPCOMING

    data class Id(override val value: String) : YouTubeId

    enum class BroadcastType { LIVE, UPCOMING, NONE, }

    companion object {
        val YouTubeVideo.url: String get() = "https://youtube.com/watch?v=${id.value}"
        val YouTubeVideo.isArchived: Boolean
            get() = liveBroadcastContent != null && (!isLiveStream() || actualEndDateTime != null)
    }
}
