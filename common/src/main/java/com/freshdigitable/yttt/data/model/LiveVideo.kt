package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.feature.timetable.TimetablePage
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

interface LiveVideoThumbnail {
    val id: LiveVideo.Id
    val title: String
    val thumbnailUrl: String
    val isLandscape: Boolean get() = true

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface LiveTimelineItem : LiveVideoThumbnail {
    val channel: LiveChannel
    val dateTime: Instant
    val isPinned: Boolean?
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface LiveVideoDetail {
    val id: LiveVideo.Id
    val channel: LiveChannel
    val thumbnailUrl: String
    val isLandscape: Boolean get() = true
    val title: AnnotatableString
    val description: AnnotatableString
    val dateTime: Instant?
    val viewerCount: BigInteger?
    val contentType: TimetablePage
}

interface LiveVideo {
    @Serializable(with = LiveVideoIdSerializer::class)
    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId

    companion object {
        val UPCOMING_DEADLINE: Duration = Duration.ofHours(6)
    }
}
