package com.freshdigitable.yttt.feature.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class YouTubeAnnotatableStringFactoryTest {

    @RunWith(Parameterized::class)
    class DescriptionAnnotationParameterizedTest(private val param: Param) {
        private val sut: YouTubeAnnotatableStringFactory by lazy {
            YouTubeAnnotatableStringFactory()
        }

        companion object {
            @JvmStatic
            @Parameters(name = "{0}")
            fun params(): List<Param> = listOf(
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
            )
        }

        @Test
        fun test() {
            // exercise
            val actual = sut.invoke(param.description)
            // verify
            assertNotNull(actual)
            assertEquals(
                param.expected.size,
                actual.annotationRangeItems.size,
            )
            param.expected.forEachIndexed { i, e ->
                val a = actual.annotationRangeItems[i]
                assertEquals(e.range, a.range)
                assertEquals(e.text, a.text)
                assertEquals(e.url, a.url)
            }
        }

        class Param(
            val name: String,
            val description: String,
            val expected: List<Expected>,
        ) {
            class Expected(
                startPosition: Int,
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
    }
}
