package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.regex.Pattern

interface FindLiveVideoUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo?
}

interface FindLiveVideoDetailAnnotatedUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideoDetailAnnotated?
}

interface LiveVideoDetailAnnotated : LiveVideoDetail {
    val descriptionAnnotationRangeItems: List<LinkAnnotationRange>

    companion object {
        // https://stackoverflow.com/questions/36586166/android-patterns-web-url-broken
//        private val WEB_URL_REGEX = Patterns.WEB_URL.toRegex()
        private val WEB_URL_REGEX = Regex("""http(s)?://[-\w.]+(:(\d+))?(/[^\s)]*)?""")
        private val YOUTUBE_URL_REGEX =
            Regex("""(?<!http(s)?://(www.)?)(www.)?youtube.com(/[^\s)]*)?""")
        val LiveVideoDetail.descriptionUrlAnnotation: List<LinkAnnotationRange>
            get() = (WEB_URL_REGEX.findAll(description).map {
                LinkAnnotationRange.Url(range = it.range, text = it.value)
            } + YOUTUBE_URL_REGEX.findAll(description).map {
                LinkAnnotationRange.Url(
                    range = it.range,
                    text = it.value,
                    url = "https://${it.value}",
                )
            }).toList().sortedBy { it.range.first }
        private val REGEX_HASHTAG =
        // Pattern.UNICODE_CHARACTER_CLASS is not supported
//            Pattern.compile("""([#|＃])(\w)+[^\s()]*""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
            Pattern.compile("""([#|＃])[^\s　()<>{}\[\]（）【】「」『』#$'",.;:|\\]+""").toRegex()
        val LiveVideoDetail.descriptionHashTagAnnotation: List<LinkAnnotationRange>
            get() = REGEX_HASHTAG.findAll(description).map {
                LinkAnnotationRange.Hashtag(range = it.range, text = it.value)
            }.toList()
        private val REGEX_ACCOUNT = Pattern.compile("""@([\w_.-]{3,30})""").toRegex()
        fun LiveVideoDetail.descriptionAccountAnnotation(
            urlCreator: (String) -> List<String>,
        ): List<LinkAnnotationRange> {
            return REGEX_ACCOUNT.findAll(description).map {
                LinkAnnotationRange.Account(
                    range = it.range,
                    text = it.value,
                    urlCandidate = urlCreator(it.value),
                )
            }.toList()
        }
    }
}

data class LiveVideoDetailAnnotatedEntity(
    private val detail: LiveVideoDetail,
    override val descriptionAnnotationRangeItems: List<LinkAnnotationRange>,
) : LiveVideoDetailAnnotated, LiveVideoDetail by detail

sealed interface LinkAnnotationRange {
    val range: IntRange
    val url: String
    val text: String
    val tag: String
        get() = json.encodeToString(serializer, this)

    @Serializable
    data class Url(
        @Contextual
        override val range: IntRange,
        override val text: String,
        override val url: String = text,
    ) : LinkAnnotationRange {
        private fun ellipsizeTextIfNeeded(
            totalLength: Int = 40,
            ellipsis: String = "...",
        ): String {
            if (text.length <= totalLength) return text
            return "${text.substring(0 until totalLength - ellipsis.length)}$ellipsis"
        }

        companion object {
            fun Url.ellipsize(totalLength: Int = 40, ellipsis: String = "..."): EllipsizedUrl =
                EllipsizedUrl(range, ellipsizeTextIfNeeded(totalLength, ellipsis), url)
        }
    }

    @Serializable
    data class EllipsizedUrl(
        @Contextual
        override val range: IntRange,
        override val text: String,
        override val url: String,
    ) : LinkAnnotationRange

    @Serializable
    data class Hashtag(
        @Contextual
        override val range: IntRange,
        override val text: String,
    ) : LinkAnnotationRange {
        override val url: String = "$TWITTER_SEARCH_API${text.drop(1)}"

        companion object {
            private const val TWITTER_SEARCH_API = "https://twitter.com/search?q=%23"
        }
    }

    @Serializable
    data class Account(
        @Contextual
        override val range: IntRange,
        override val text: String,
        val urlCandidate: List<String>,
    ) : LinkAnnotationRange {
        override val url: String
            get() = urlCandidate.first()
    }

    companion object {
        private val module = SerializersModule {
            polymorphic(LinkAnnotationRange::class) {
                subclass(Url.serializer())
                subclass(EllipsizedUrl.serializer())
                subclass(Hashtag.serializer())
                subclass(Account.serializer())
            }
            contextual(IntRange::class, IntRangeSerializer)
        }
        private val json = Json {
            serializersModule = module
        }
        private val serializer = PolymorphicSerializer(LinkAnnotationRange::class)

        fun createFromTag(tag: String): LinkAnnotationRange = json.decodeFromString(tag)
    }
}

internal object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(IntRange::class.java.name) {
            element<Int>("start")
            element<Int>("endInclusive")
        }

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.start)
            encodeIntElement(descriptor, 1, value.endInclusive)
        }
    }

    override fun deserialize(decoder: Decoder): IntRange = decoder.decodeStructure(descriptor) {
        var s = -1
        var e = -1
        while (true) {
            when (val i = decodeElementIndex(descriptor)) {
                0 -> s = decodeIntElement(descriptor, i)
                1 -> e = decodeIntElement(descriptor, i)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("unexpected index: $i")
            }
        }
        require(s >= 0 && e >= 0 && s < e)
        IntRange(start = s, endInclusive = e)
    }
}
