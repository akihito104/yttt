package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeAnnotatableStringFactory
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class YouTubeAnnotatableStringFactoryTest : ShouldSpec(
    {
        withData(
            mapOf(
                "url" to Param(
                    description = "https://example.com/",
                    expected = listOf(Param.Expected.url(0, "https://example.com/")),
                ),
                "hashtag" to Param(
                    description = "#hashtag1",
                    expected = listOf(Param.Expected.hashtag(0, "#hashtag1")),
                ),
                "account" to Param(
                    description = "@account01",
                    expected = listOf(Param.Expected.account(0, "@account01")),
                ),
                "hashtag and url" to Param(
                    description = """
                    |#hashtag1
                    |https://example.com/
                    |main: #hash_main
                    |fa: #hash_fa
                    """.trimMargin(),
                    expected = listOf(
                        Param.Expected.hashtag(0, "#hashtag1"),
                        Param.Expected.url(10, "https://example.com/"),
                        Param.Expected.hashtag(10 + 21 + 6, "#hash_main"),
                        Param.Expected.hashtag(10 + 21 + 17 + 4, "#hash_fa"),
                    ),
                ),
                "url has anchor" to Param(
                    description = """
                    |#hashtag1
                    |https://example.com/#example
                    """.trimMargin(),
                    expected = listOf(
                        Param.Expected.hashtag(0, "#hashtag1"),
                        Param.Expected.url(10, "https://example.com/#example"),
                    ),
                ),
                "url has account" to Param(
                    description = """
                    |#hashtag1
                    |@account01 ( https://example.com/@account01 )
                    """.trimMargin(),
                    expected = listOf(
                        Param.Expected.hashtag(0, "#hashtag1"),
                        Param.Expected.account(10, "@account01"),
                        Param.Expected.url(10 + 13, "https://example.com/@account01"),
                    ),
                ),
                "no schema (well-known url: youtube.com)" to Param(
                    description = """
                    |goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join
                    """.trimMargin(),
                    expected = listOf(
                        Param.Expected.url(8, "https://example.com/goods"),
                        Param.Expected.url(
                            34 + 13,
                            text = "www.youtube.com/@akihito104/join",
                            url = "https://www.youtube.com/@akihito104/join",
                        ),
                    ),
                ),
                "item order with no schema (well-known url: youtube.com)" to Param(
                    description = """
                    |goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join
                    |back number 1 - https://youtube.com/playlist?list=example
                    """.trimMargin(),
                    expected = listOf(
                        Param.Expected.url(8, "https://example.com/goods"),
                        Param.Expected.url(
                            34 + 13,
                            text = "www.youtube.com/@akihito104/join",
                            url = "https://www.youtube.com/@akihito104/join",
                        ),
                        Param.Expected.url(
                            34 + 46 + 16,
                            "https://youtube.com/playlist?list=example",
                        ),
                    ),
                ),
            ),
        ) { (description, expected) ->
            // exercise
            var factory = YouTubeAnnotatableStringFactory()
            val actual = factory.create(description)
            // verify
            actual.shouldNotBeNull()
            actual.annotationRangeItems.size shouldBe expected.size
            expected.forEachIndexed { i, e ->
                val a = actual.annotationRangeItems[i]
                a.range shouldBe e.range
                a.text shouldBe e.text
                a.url shouldBe e.url
            }
        }
    },
)

internal data class Param(
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
                startPosition,
                text,
                url = "https://youtube.com/$text",
            )
        }
    }
}
