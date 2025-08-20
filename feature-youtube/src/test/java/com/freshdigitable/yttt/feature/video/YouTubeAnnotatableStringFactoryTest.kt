package com.freshdigitable.yttt.feature.video

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class YouTubeAnnotatableStringFactoryTest : ShouldSpec({
    val sut = YouTubeAnnotatableStringFactory()
    withData(
        nameFn = { it.name },
        Param(
            name = "url",
            description = "https://example.com/",
            expected = listOf(Param.Expected.url(0, "https://example.com/"))
        ),
        Param(
            name = "hashtag",
            description = "#hashtag1",
            expected = listOf(Param.Expected.hashtag(0, "#hashtag1"))
        ),
        Param(
            name = "account",
            description = "@account01",
            expected = listOf(Param.Expected.account(0, "@account01")),
        ),
        Param(
            name = "hashtag and url",
            description = """#hashtag1
                        |https://example.com/
                        |main: #hash_main
                        |fa: #hash_fa
                    """.trimMargin(),
            expected = listOf(
                Param.Expected.hashtag(0, "#hashtag1"),
                Param.Expected.url(10, "https://example.com/"),
                Param.Expected.hashtag(10 + 21 + 6, "#hash_main"),
                Param.Expected.hashtag(10 + 21 + 17 + 4, "#hash_fa"),
            )
        ),
        Param(
            name = "url has anchor",
            description = """#hashtag1
                        |https://example.com/#example
                    """.trimMargin(),
            expected = listOf(
                Param.Expected.hashtag(0, "#hashtag1"),
                Param.Expected.url(10, "https://example.com/#example"),
            )
        ),
        Param(
            name = "url has account",
            description = """#hashtag1
                        |@account01 ( https://example.com/@account01 )
                    """.trimMargin(),
            expected = listOf(
                Param.Expected.hashtag(0, "#hashtag1"),
                Param.Expected.account(10, "@account01"),
                Param.Expected.url(10 + 13, "https://example.com/@account01"),
            ),
        ),
    ) { param ->
        // exercise
        val actual = sut.invoke(param.description)
        // verify
        actual.shouldNotBeNull()
        actual.annotationRangeItems.size shouldBe param.expected.size
        param.expected.forEachIndexed { i, e ->
            val a = actual.annotationRangeItems[i]
            a.range shouldBe e.range
            a.text shouldBe e.text
            a.url shouldBe e.url
        }
    }
})

internal data class Param(
    val name: String,
    val description: String,
    val expected: List<Expected>,
) {
    internal data class Expected(
        val startPosition: Int,
        val text: String,
        val url: String = text,
    ) {
        val range: IntRange = startPosition until (startPosition + text.length)

        companion object {
            fun url(startPosition: Int, text: String, url: String = text): Expected =
                Expected(startPosition, text, url)

            fun hashtag(startPosition: Int, text: String): Expected = Expected(
                startPosition,
                text,
                url = "https://twitter.com/search?q=%23${text.substring(1)}",
            )

            fun account(startPosition: Int, text: String): Expected = Expected(
                startPosition, text, url = "https://youtube.com/$text",
            )
        }
    }

    override fun toString(): String = name
}
