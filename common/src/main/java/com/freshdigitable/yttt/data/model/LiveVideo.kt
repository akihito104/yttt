package com.freshdigitable.yttt.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
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

private class LiveVideoIdSerializer : KSerializer<LiveVideo.Id> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(LiveVideo.Id::class.java.name) {
            element<String>("value")
            element<KClass<out IdBase>>("type")
        }

    override fun deserialize(
        decoder: Decoder,
    ): LiveVideo.Id = decoder.decodeStructure(descriptor) {
        var value: String? = null
        var type: KClass<out IdBase>? = null
        while (true) {
            when (val i = decodeElementIndex(descriptor)) {
                0 -> value = decodeStringElement(descriptor, i)
                1 -> type =
                    decodeSerializableElement(descriptor, i, serializer<KClass<out IdBase>>())

                CompositeDecoder.DECODE_DONE -> break
                else -> throw IllegalStateException("Unexpected index: $i")
            }
        }
        LiveVideo.Id(
            checkNotNull(value) { "value is null" },
            checkNotNull(type) { "type is null" },
        )
    }

    override fun serialize(encoder: Encoder, value: LiveVideo.Id) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.value)
            encodeSerializableElement(
                descriptor, 1, serializer<KClass<out IdBase>>(), value.type,
            )
        }
    }
}
