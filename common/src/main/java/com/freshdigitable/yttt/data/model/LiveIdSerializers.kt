package com.freshdigitable.yttt.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

internal abstract class IdBaseSerializer<T : LiveId>(
    descriptorName: String,
    private val deserializeLiveId: (String, KClass<out IdBase>) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(descriptorName) {
        element<KClass<out IdBase>>("type")
        element<String>("value")
    }

    override fun deserialize(
        decoder: Decoder,
    ): T = decoder.decodeStructure(descriptor) {
        var value: String? = null
        var type: KClass<out IdBase>? = null
        while (true) {
            when (val i = decodeElementIndex(descriptor)) {
                0 -> type =
                    decodeSerializableElement(descriptor, i, serializer<KClass<out IdBase>>())

                1 -> value = decodeStringElement(descriptor, i)

                CompositeDecoder.DECODE_DONE -> break
                else -> throw IllegalStateException("Unexpected index: $i")
            }
        }
        deserializeLiveId(
            checkNotNull(value) { "value is null" },
            checkNotNull(type) { "type is null" },
        )
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor, 0, serializer<KClass<out IdBase>>(), value.type,
            )
            encodeStringElement(descriptor, 1, value.value)
        }
    }
}

internal class LiveChannelIdSerializer : IdBaseSerializer<LiveChannel.Id>(
    LiveChannel.Id::class.java.name,
    LiveChannel::Id,
)

internal class LiveVideoIdSerializer : IdBaseSerializer<LiveVideo.Id>(
    LiveVideo.Id::class.java.name,
    { value, type -> LiveVideo.Id(value, type) }
)
