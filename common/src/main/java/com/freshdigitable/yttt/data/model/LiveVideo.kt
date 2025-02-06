package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant
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
    val url: String
    val description: String
    val viewerCount: BigInteger?

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId

    companion object

    interface OnAir : LiveVideo {
        override val actualStartDateTime: Instant
    }

    interface Upcoming : LiveVideo {
        override val scheduledStartDateTime: Instant
    }

    interface FreeChat : LiveVideo {
        override val scheduledStartDateTime: Instant
    }
}

interface LiveVideoForDetail {
    val video: LiveVideo
    val annotatableDescription: AnnotatableString
    val annotatableTitle: AnnotatableString
}
