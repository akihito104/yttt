package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant

interface LiveVideo {
    val id: Id
    val title: String
    val channel: LiveChannel
    val thumbnailUrl: String
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?
    val isFreeChat: Boolean? get() = null

    fun isLiveStream(): Boolean = scheduledStartDateTime != null
    fun isNowOnAir(): Boolean = actualStartDateTime != null && actualEndDateTime == null
    fun isUpcoming(): Boolean = isLiveStream() && actualStartDateTime == null

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val platform: LivePlatform = LivePlatform.YOUTUBE,
    ) : IdBase<String>
}

data class LiveVideoEntity(
    override val id: LiveVideo.Id,
    override val channel: LiveChannel,
    override val title: String,
    override val scheduledStartDateTime: Instant?,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val thumbnailUrl: String,
) : LiveVideo

interface LiveVideoDetail : LiveVideo {
    val description: String
    val viewerCount: BigInteger?
}
