package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LinkAnnotationRange
import com.freshdigitable.yttt.data.model.LinkAnnotationRange.Url.Companion.ellipsize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class LiveVideoDetailAnnotatedTest {
    class Fundamental {
        @Test
        fun serializableUrl() {
            val sut = LinkAnnotationRange.Url(range = 0..10, text = "https://example.com/")
            val actual = LinkAnnotationRange.createFromTag(sut.tag)
            assertEquals(sut, actual)
        }

        @Test
        fun serializableAccount() {
            val sut = LinkAnnotationRange.Account(
                range = 0..10,
                text = "@account01",
                urlCandidate = listOf("https://example.com/@account01"),
            )
            val actual = LinkAnnotationRange.createFromTag(sut.tag)
            assertEquals(sut, actual)
        }
    }

    @RunWith(Parameterized::class)
    class EllipsizeText(private val param: TestParam) {
        companion object {
            @JvmStatic
            @Parameters
            fun params(): List<TestParam> = listOf(
                TestParam(
                    text = "https://example.com/",
                    expected = "https://example.com/",
                ),
                TestParam(
                    text = "https://example.com/very/long/but/ellips",
                    expected = "https://example.com/very/long/but/ellips",
                ),
                TestParam(
                    text = "https://example.com/very/long/so/ellipize",
                    expected = "https://example.com/very/long/so/elli...",
                ),
                TestParam(
                    text = "https://example.com/very/long/so/ellipized/this",
                    ellipsis = "…",
                    expected = "https://example.com/very/long/so/ellipi…",
                ),
            )
        }

        @Test
        fun test() {
            // setup
            val sut = LinkAnnotationRange.Url(
                text = param.text,
                range = 0 until param.text.length, // do not care
            )
            // exercise
            val actual = sut.ellipsize(param.totalLength, param.ellipsis).text
            // verify
            assertEquals(param.expected, actual)
        }

        data class TestParam(
            val text: String,
            val totalLength: Int = 40,
            val ellipsis: String = "...",
            val expected: String,
        )
    }

    @RunWith(Parameterized::class)
    class AnnotationParameterizedTest(
        private val param: TestParam,
    ) {
        companion object {
            @JvmStatic
            @Parameters(name = "{0}")
            fun params(): List<TestParam> = listOf(
                TestParam(
                    name = "empty description",
                    description = "",
                    expected = emptyList(),
                ),
                TestParam(
                    name = "has trailing slash",
                    description = "https://example.com/",
                    expected = listOf(TestParam.Expected.url(0, "https://example.com/")),
                ),
                TestParam(
                    name = "no trailing slash",
                    description = "https://example.com",
                    expected = listOf(TestParam.Expected.url(0, "https://example.com")),
                ),
                TestParam(
                    name = "has caption",
                    description = "link - https://example.com/",
                    expected = listOf(TestParam.Expected.url(7, "https://example.com/")),
                ),
                TestParam(
                    name = "multibyte and line feed",
                    description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                    expected = listOf(TestParam.Expected.url(7 + 7, "https://example.com")),
                ),
                TestParam(
                    name = "1 line contains 2 urls",
                    description = """18:00 https://example.com/@account1 https://example.com/@account2
                    |19:00 https://example.com/@account3 https://example.com/@account4
                    |""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.url(6, "https://example.com/@account1"),
                        TestParam.Expected.url(36, "https://example.com/@account2"),
                        TestParam.Expected.url(66 + 6, "https://example.com/@account3"),
                        TestParam.Expected.url(66 + 36, "https://example.com/@account4"),
                    ),
                ),
                TestParam(
                    name = "trailing unexpected parentheses",
                    description = """illust.: account00 (https://example.com/account00)
                        |mix: account01 【https://example.com/account01】
                    """.trimMargin(),
                    expected = listOf(
                        TestParam.Expected.url(20, "https://example.com/account00"),
                        TestParam.Expected.url(51 + 16, "https://example.com/account01"),
                    )
                ),
                TestParam(
                    name = "no schema (well-known url: youtube.com)",
                    description = """goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.url(8, "https://example.com/goods"),
                        TestParam.Expected.url(
                            34 + 13,
                            text = "www.youtube.com/@akihito104/join",
                            url = "https://www.youtube.com/@akihito104/join",
                        ),
                    ),
                ),
                TestParam(
                    name = "has schema (well-known url: youtube.com)",
                    description = """goods - https://example.com/goods
                    |membership - https://www.youtube.com/@akihito104/join""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.url(8, "https://example.com/goods"),
                        TestParam.Expected.url(
                            34 + 13,
                            "https://www.youtube.com/@akihito104/join",
                        ),
                    ),
                ),
                TestParam(
                    name = "item order with no schema (well-known url: youtube.com)",
                    description = """goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join
                    |back number 1 - https://youtube.com/playlist?list=example
                    |""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.url(8, "https://example.com/goods"),
                        TestParam.Expected.url(
                            34 + 13,
                            text = "www.youtube.com/@akihito104/join",
                            url = "https://www.youtube.com/@akihito104/join",
                        ),
                        TestParam.Expected.url(
                            34 + 46 + 16,
                            "https://youtube.com/playlist?list=example",
                        ),
                    ),
                ),
                TestParam(
                    name = "no line feed",
                    description = "fun art: #hashtag",
                    expected = listOf(TestParam.Expected.hashtag(9, "#hashtag")),
                ),
                TestParam(
                    name = "has line feed",
                    description = """|
                    |fun art: #hashtag
                    |""".trimMargin(),
                    expected = listOf(TestParam.Expected.hashtag(10, "#hashtag")),
                ),
                TestParam(
                    name = "multiple hashtags",
                    description = """#hashtag1
                    |#hashtag2 #hashtag3
                    |""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.hashtag(0, "#hashtag1"),
                        TestParam.Expected.hashtag(10, "#hashtag2"),
                        TestParam.Expected.hashtag(10 + 10, "#hashtag3"),
                    ),
                ),
                TestParam(
                    name = "multibyte (ja)",
                    description = """配信タグ: #ハッシュタグ
                    |ファンアート: ＃全角ハッシュタグ　＃全角ハッシュタグR18
                    |本日のタグ：【#全角ハッシュタグコラボ】
                    |""".trimMargin(),
                    expected = listOf(
                        TestParam.Expected.hashtag(6, "#ハッシュタグ"),
                        TestParam.Expected.hashtag(14 + 8, "＃全角ハッシュタグ"),
                        TestParam.Expected.hashtag(14 + 18, "＃全角ハッシュタグR18"),
                        TestParam.Expected.hashtag(14 + 31 + 7, "#全角ハッシュタグコラボ"),
                    ),
                ),
                TestParam(
                    name = "simple",
                    description = "@account01",
                    expected = listOf(TestParam.Expected.account(0, "@account01")),
                ),
                TestParam(
                    name = "multiple at line",
                    description = """@account01
                        |@account02 @account03
                    """.trimMargin(),
                    expected = listOf(
                        TestParam.Expected.account(0, "@account01"),
                        TestParam.Expected.account(11, "@account02"),
                        TestParam.Expected.account(11 + 11, "@account03"),
                    ),
                ),
                TestParam(
                    name = "account in url", // should remove in post process
                    description = """@account01
                        |https://www.youtube.com/@account02/join
                    """.trimMargin(),
                    expected = listOf(
                        TestParam.Expected.account(0, "@account01"),
                        TestParam.Expected.url(0 + 11, "https://www.youtube.com/@account02/join"),
                    ),
                ),
                TestParam(
                    name = "account with parenthesis",
                    description = """account01 (@account01)
                        |account02 【@account02】
                    """.trimMargin(),
                    expected = listOf(
                        TestParam.Expected.account(11, "@account01"),
                        TestParam.Expected.account(23 + 11, "@account02"),
                    ),
                ),
            )
        }

        @Test
        fun test() {
            // setup
            // exercise
            val actual = AnnotatableString.create(param.description) {
                listOf("https://example.com/$it")
            }
            // assertion
            assertEquals(param.expected.size, actual.annotationRangeItems.size)
            param.expected.forEachIndexed { i, e ->
                val a = actual.annotationRangeItems[i]
                assertEquals(e.range, a.range)
                assertEquals(e.text, a.text)
                assertEquals(e.url, a.url)
            }
        }

        data class TestParam(
            private val name: String? = null,
            val description: String,
            val expected: List<Expected>,
        ) {
            data class Expected(
                private val startPosition: Int,
                val text: String,
                val url: String = text,
            ) {
                val range: IntRange = startPosition until (startPosition + text.length)

                companion object {
                    fun url(startPosition: Int, text: String, url: String = text): Expected =
                        Expected(startPosition, text, url)

                    fun hashtag(startPosition: Int, text: String): Expected {
                        val url = "https://twitter.com/search?q=%23${text.substring(1)}"
                        return Expected(startPosition, text, url)
                    }

                    fun account(startPosition: Int, text: String): Expected =
                        Expected(startPosition, text, "https://example.com/$text")
                }
            }

            override fun toString(): String = name ?: description.lines().first()
        }
    }
}
