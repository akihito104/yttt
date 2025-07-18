package com.freshdigitable.yttt.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

interface AnnotatableString {
    val annotatable: String
    val annotationRangeItems: List<LinkAnnotationRange>

    companion object {
        private const val PARENTHESIS = "()<>{}\\[\\]（）【】「」『』〖〗"
        private const val SEPARATOR = """,.;:|/\￤┊!！?？"""

        // https://stackoverflow.com/questions/36586166/android-patterns-web-url-broken
//        private val WEB_URL_REGEX = Patterns.WEB_URL.toRegex()
        private val WEB_URL_REGEX = Regex("""http(s)?://[-\w.]+(:(\d+))?(/[^\s$PARENTHESIS]*)?""")
        private val YOUTUBE_URL_REGEX =
            Regex("""(?<!http(s)?://(www.)?)(www.)?youtube.com(/[^\s$PARENTHESIS]*)?""")

        private fun urlAnnotationRange(annotatable: String): List<LinkAnnotationRange> {
            val url = WEB_URL_REGEX.findAll(annotatable).map {
                LinkAnnotationRange.Url(range = it.range, text = it.value)
            }
            val youtubeUrl = YOUTUBE_URL_REGEX.findAll(annotatable)
                .filter { r -> url.all { !it.range.contains(r.range.first) } }
                .map {
                    LinkAnnotationRange.Url(
                        range = it.range,
                        text = it.value,
                        url = "https://${it.value}",
                    )
                }
            return (url + youtubeUrl).toList().sortedBy { it.range.first }
        }

        private val REGEX_HASHTAG =
        // Pattern.UNICODE_CHARACTER_CLASS is not supported
//            Pattern.compile("""([#＃])(\w)+[^\s()]*""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
            Regex("""([#＃])[^\s　$PARENTHESIS#$'"$SEPARATOR]+""")

        private fun hashTagAnnotationRange(annotatable: String): List<LinkAnnotationRange> {
            return REGEX_HASHTAG.findAll(annotatable).filter { a ->
                !a.value.toCharArray(startIndex = 1).all { it.isDigit() }
            }.map {
                LinkAnnotationRange.Hashtag(range = it.range, text = it.value)
            }.toList()
        }

        private val REGEX_ACCOUNT = Regex("""@([\w_.-]{3,30})""")
        private fun accountAnnotationRange(
            annotatable: String,
            urlCreator: (String) -> List<String>,
        ): List<LinkAnnotationRange> {
            return REGEX_ACCOUNT.findAll(annotatable).map {
                LinkAnnotationRange.Account(
                    range = it.range,
                    text = it.value,
                    urlCandidate = urlCreator(it.value),
                )
            }.toList()
        }

        fun create(
            annotatable: String,
            /**
             * at-mark-started string (such as @account01) is passed.
             */
            accountUrlCreator: (String) -> List<String>,
        ): AnnotatableString {
            if (annotatable.isEmpty()) {
                return empty()
            }
            val urlAnnotation = urlAnnotationRange(annotatable)
            val hashtagAnnotation = hashTagAnnotationRange(annotatable).filter { a ->
                urlAnnotation.all { !it.contains(a) }
            }
            val accountAnnotation =
                accountAnnotationRange(annotatable, accountUrlCreator).filter { a ->
                    urlAnnotation.all { !it.contains(a) }
                }
            return AnnotatableStringImpl(
                annotatable,
                (urlAnnotation + hashtagAnnotation + accountAnnotation).sortedBy { it.range.first },
            )
        }

        fun empty(): AnnotatableString = EmptyAnnotatableString
    }

    private data class AnnotatableStringImpl(
        override val annotatable: String,
        override val annotationRangeItems: List<LinkAnnotationRange>
    ) : AnnotatableString

    private object EmptyAnnotatableString : AnnotatableString {
        override val annotatable: String = ""
        override val annotationRangeItems: List<LinkAnnotationRange> = emptyList()
    }
}

private val module = SerializersModule {
    polymorphic(LinkAnnotationRange::class) {
        subclass(LinkAnnotationRange.Url::class)
        subclass(LinkAnnotationRange.EllipsizedUrl::class)
        subclass(LinkAnnotationRange.Hashtag::class)
        subclass(LinkAnnotationRange.Account::class)
    }
    contextual(IntRange::class, IntRangeSerializer)
}
private val json = Json {
    serializersModule = module
}
private val serializer = PolymorphicSerializer(LinkAnnotationRange::class)

sealed interface LinkAnnotationRange {
    val range: IntRange
    val url: String
    val text: String
    val tag: String
        get() = json.encodeToString(serializer, this)

    fun contains(other: LinkAnnotationRange): Boolean = range.contains(other.range.first)

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

class LinkAnnotationDialogState {
    var currentDialog by mutableStateOf<LinkAnnotationRange?>(null)
        private set

    fun showDialog(dialog: LinkAnnotationRange) {
        currentDialog = dialog
    }

    fun dismiss() {
        currentDialog = null
    }
}
