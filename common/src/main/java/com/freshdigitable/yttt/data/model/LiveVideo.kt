package com.freshdigitable.yttt.data.model

import kotlinx.serialization.Serializable
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

interface LiveVideo<T : LiveVideo<T>> : LiveVideoThumbnail, Comparable<T> {
    val channel: LiveChannel
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?
    val url: String
    val description: String
    val viewerCount: BigInteger?

    override fun compareTo(other: T): Int {
        val type = this.id.type.java.simpleName.compareTo(other.id.type.java.simpleName)
        if (type != 0) return type
        return this.id.value.compareTo(other.id.value)
    }

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    @Serializable(with = LiveVideoIdSerializer::class)
    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId

    companion object

    interface OnAir : LiveVideo<OnAir> {
        override val actualStartDateTime: Instant
        override fun compareTo(other: OnAir): Int = comparator.compare(this, other)

        companion object {
            private val comparator: Comparator<OnAir> = Comparator { p0, p1 ->
                // desc. order for actualStartDateTime
                val date = p1.actualStartDateTime.compareTo(p0.actualStartDateTime)
                if (date != 0) return@Comparator date
                p0.title.compareTo(p1.title)
            }
        }
    }

    interface Upcoming : LiveVideo<Upcoming> {
        override val scheduledStartDateTime: Instant
        override fun compareTo(other: Upcoming): Int = comparator.compare(this, other)

        companion object {
            private val comparator: Comparator<Upcoming> = Comparator { p0, p1 ->
                val date = p0.scheduledStartDateTime.compareTo(p1.scheduledStartDateTime)
                if (date != 0) return@Comparator date
                p0.title.compareTo(p1.title)
            }
        }
    }

    interface FreeChat : LiveVideo<FreeChat> {
        override val scheduledStartDateTime: Instant
        override fun compareTo(other: FreeChat): Int = comparator.compare(this, other)

        companion object {
            private val comparator: Comparator<FreeChat> = Comparator { p0, p1 ->
                val channelId = p0.channel.id.value.compareTo(p1.channel.id.value)
                if (channelId != 0) return@Comparator channelId
                val date = p0.scheduledStartDateTime.compareTo(p1.scheduledStartDateTime)
                if (date != 0) return@Comparator date
                p0.title.compareTo(p1.title)
            }
        }
    }
}
