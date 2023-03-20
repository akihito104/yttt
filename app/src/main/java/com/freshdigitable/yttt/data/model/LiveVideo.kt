package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LiveVideo {
    val id: Id
    val title: String
    val channel: LiveChannel
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?

    fun isLiveStream(): Boolean = scheduledStartDateTime != null
    fun isNowOnAir(): Boolean = actualStartDateTime != null && actualEndDateTime == null
    fun isUpcoming(): Boolean = isLiveStream() && actualStartDateTime == null

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(override val value: String) : IdBase<String>
}

data class LiveVideoEntity(
    override val id: LiveVideo.Id,
    override val channel: LiveChannel,
    override val title: String,
    override val scheduledStartDateTime: Instant?,
    override val scheduledEndDateTime: Instant?,
    override val actualStartDateTime: Instant?,
    override val actualEndDateTime: Instant?
) : LiveVideo
