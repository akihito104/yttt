package com.freshdigitable.yttt.data.model

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
        fun empty(): AnnotatableString = EmptyAnnotatableString
    }

    interface Factory {
        companion object {
            private const val PARENTHESIS = "()<>{}\\[\\]（）【】「」『』〖〗"
            private const val SEPARATOR = """,.;:|/\￤┊!！?？"""

            // https://stackoverflow.com/questions/36586166/android-patterns-web-url-broken
//        private val WEB_URL_REGEX = Patterns.WEB_URL.toRegex()
            private val WEB_URL_REGEX =
                Regex("""http(s)?://[-\w.]+(:(\d+))?(/[^\s$PARENTHESIS]*)?""")

            // Pattern.UNICODE_CHARACTER_CLASS is not supported
            // Pattern.compile("""([#＃])(\w)+[^\s()]*""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
            private val REGEX_HASHTAG =
                Regex("""([#＃])[^\s　$PARENTHESIS#$'"$SEPARATOR]+""")
            private val REGEX_ACCOUNT = Regex("""@([\w_.-]{3,30})""")

            fun empty(): Factory = factory { emptyList() }
            fun factory(accountUrlCandidate: (String) -> List<String>): Factory = object : Factory {
                override fun accountUrlCandidate(account: String): List<String> =
                    accountUrlCandidate(account)
            }
        }

        fun create(annotatable: String): AnnotatableString {
            if (annotatable.isEmpty()) {
                return AnnotatableString.empty()
            }
            val urlAnnotation = urlAnnotation(annotatable)
            val hashtagAnnotation = hashTagAnnotation(annotatable).filter { a ->
                urlAnnotation.all { !it.contains(a) }
            }
            val accountAnnotation = accountAnnotation(annotatable).filter { a ->
                urlAnnotation.all { !it.contains(a) }
            }
            return AnnotatableStringImpl(
                annotatable,
                (urlAnnotation + hashtagAnnotation + accountAnnotation).sortedBy { it.range.first },
            )
        }

        fun urlAnnotation(annotatable: String): List<LinkAnnotationRange> {
            return WEB_URL_REGEX.findAll(annotatable).map {
                LinkAnnotationRange.Url(range = it.range, text = it.value)
            }.toList()
        }

        fun hashTagAnnotation(annotatable: String): List<LinkAnnotationRange> {
            return REGEX_HASHTAG.findAll(annotatable).filter { a ->
                !a.value.toCharArray(startIndex = 1).all { it.isDigit() }
            }.map {
                LinkAnnotationRange.Hashtag(range = it.range, text = it.value)
            }.toList()
        }

        fun accountAnnotation(annotatable: String): List<LinkAnnotationRange> {
            return REGEX_ACCOUNT.findAll(annotatable).map {
                LinkAnnotationRange.Account(
                    range = it.range,
                    text = it.value,
                    urlCandidate = accountUrlCandidate(it.value),
                )
            }.toList()
        }

        fun accountUrlCandidate(account: String): List<String>
    }

    private data class AnnotatableStringImpl(
        override val annotatable: String,
        override val annotationRangeItems: List<LinkAnnotationRange>,
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
            totalLength: Int = ELLIPSIS_THRESHOLD,
            ellipsis: String = "...",
        ): String {
            if (text.length <= totalLength) return text
            return "${text.substring(0 until totalLength - ellipsis.length)}$ellipsis"
        }

        companion object {
            const val ELLIPSIS_THRESHOLD = 40
            fun Url.ellipsize(
                totalLength: Int = ELLIPSIS_THRESHOLD,
                ellipsis: String = "...",
            ): EllipsizedUrl =
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
