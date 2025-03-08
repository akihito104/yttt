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
import java.text.NumberFormat
import java.util.Locale
import kotlin.reflect.KClass

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String
    val platform: LivePlatform

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    @Serializable(with = LiveChannelIdSerializer::class)
    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
    override val platform: LivePlatform,
) : LiveChannel

interface LiveChannelDetailBody : LiveChannel {
    val bannerUrl: String?
    val statsText: String

    companion object {
        val BigInteger.toStringWithComma: String
            get() = NumberFormat.getNumberInstance(Locale.US).format(this)
        const val STATS_SEPARATOR: String = "・"
    }
}

private class LiveChannelIdSerializer : KSerializer<LiveChannel.Id> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(LiveChannel.Id::class.java.name) {
            element<String>("value")
            element<KClass<out IdBase>>("type")
        }

    override fun deserialize(
        decoder: Decoder,
    ): LiveChannel.Id = decoder.decodeStructure(descriptor) {
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
        LiveChannel.Id(
            checkNotNull(value) { "value is null" },
            checkNotNull(type) { "type is null" },
        )
    }

    override fun serialize(encoder: Encoder, value: LiveChannel.Id) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.value)
            encodeSerializableElement(
                descriptor, 1, serializer<KClass<out IdBase>>(), value.type,
            )
        }
    }
}
