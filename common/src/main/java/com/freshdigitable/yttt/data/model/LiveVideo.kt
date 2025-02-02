package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

interface LiveVideoThumbnail {
    val id: LiveVideo.Id
    val title: String
    val thumbnailUrl: String

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

data class LiveVideoThumbnailEntity(
    override val id: LiveVideo.Id,
    override val title: String,
    override val thumbnailUrl: String,
) : LiveVideoThumbnail

interface LiveVideo : LiveVideoThumbnail {
    val channel: LiveChannel
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?
    val isFreeChat: Boolean? get() = null
    val url: String

    fun isNowOnAir(): Boolean = actualStartDateTime != null && actualEndDateTime == null
    fun isUpcoming(): Boolean = scheduledStartDateTime != null && actualStartDateTime == null

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId

    interface Upcoming : LiveVideo {
        val offset: Duration
        override val scheduledStartDateTime: Instant

        companion object {
            fun Upcoming.scheduledStartLocalDateWithOffset(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
                (scheduledStartDateTime - offset).toLocalDateTime(zoneId).toLocalDate()
        }
    }
}

data class LiveVideoEntity(
    override val id: LiveVideo.Id,
    override val channel: LiveChannel,
    override val title: String,
    override val scheduledStartDateTime: Instant? = null,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val thumbnailUrl: String,
    override val url: String,
    override val isFreeChat: Boolean? = null,
    private val isNowOnAir: Boolean? = null,
    private val isUpcoming: Boolean? = null,
) : LiveVideo {
    override fun isNowOnAir(): Boolean = isNowOnAir ?: super.isNowOnAir()
    override fun isUpcoming(): Boolean = isUpcoming ?: super.isUpcoming()
}

interface LiveVideoDetail : LiveVideo {
    val description: String
    val viewerCount: BigInteger?
}

data class LiveVideoDetailAnnotatedEntity(
    private val detail: LiveVideoDetail,
    val annotatableDescription: AnnotatableString,
    val annotatableTitle: AnnotatableString,
) : LiveVideoDetail by detail
