package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.feature.video.LinkAnnotationRange.Companion.ellipsizeTextIfNeeded
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionHashTagAnnotation
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionUrlAnnotation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class LiveVideoDetailAnnotatedTest {
    @RunWith(Parameterized::class)
    class EllipsizeText(private val param: TestParam) {
        companion object {
            @JvmStatic
            @Parameters
            fun params(): List<TestParam> = listOf(
                TestParam(
                    text = "#hashtag",
                    tag = "hashtag",
                    expected = "#hashtag",
                ),
                TestParam(
                    text = "#hashtag_very_long_but_ellipsized_because_of_hashtag",
                    tag = "hashtag",
                    expected = "#hashtag_very_long_but_ellipsized_because_of_hashtag",
                ),
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
            )
        }

        @Test
        fun test() {
            // setup
            val sut = LinkAnnotationRange(
                text = param.text,
                range = 0 until param.text.length, // do not care
                tag = param.tag,
                url = "--do not care--"
            )
            // exercise
            val actual = sut.ellipsizeTextIfNeeded()
            // verify
            assertEquals(param.expected, actual)
        }

        data class TestParam(
            val text: String,
            val tag: String = "URL",
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
                TestParam.url(
                    name = "empty description",
                    description = "",
                    expected = emptyList(),
                ),
                TestParam.url(
                    name = "has trailing slash",
                    description = "https://example.com/",
                    TestParam.Expected.url(0, "https://example.com/"),
                ),
                TestParam.url(
                    name = "no trailing slash",
                    description = "https://example.com",
                    TestParam.Expected.url(0, "https://example.com"),
                ),
                TestParam.url(
                    name = "has caption",
                    description = "link - https://example.com/",
                    TestParam.Expected.url(7, "https://example.com/"),
                ),
                TestParam.url(
                    name = "multibyte and line feed",
                    description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                    TestParam.Expected.url(7 + 7, "https://example.com"),
                ),
                TestParam.url(
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
                TestParam.url(
                    name = "trailing unexpected parentheses",
                    description = "illust.: account00 (https://example.com/account00)",
                    TestParam.Expected.url(20, "https://example.com/account00")
                ),
                TestParam.url(
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
                TestParam.url(
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
                TestParam.url(
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
                TestParam.hashtag(
                    name = "empty description",
                    description = "",
                    expected = emptyList(),
                ),
                TestParam.hashtag(
                    name = "no line feed",
                    description = "fun art: #hashtag",
                    TestParam.Expected.hashtag(9, "#hashtag"),
                ),
                TestParam.hashtag(
                    name = "has line feed",
                    description = """|
                    |fun art: #hashtag
                    |""".trimMargin(),
                    TestParam.Expected.hashtag(10, "#hashtag"),
                ),
                TestParam.hashtag(
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
                TestParam.hashtag(
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
            )
        }

        @Test
        fun test() {
            // setup
            val detail = mockk<LiveVideoDetail>().apply {
                every { description } returns param.description
            }
            // exercise
            val actual = param.sut(detail)
            // assertion
            assertEquals(param.expected.size, actual.size)
            param.expected.forEachIndexed { i, e ->
                val a = actual[i]
                assertEquals(e.range, a.range)
                assertEquals(e.text, a.text)
                assertEquals(e.url, a.url)
                assertEquals(e.tag, a.tag)
            }
        }

        data class TestParam(
            private val name: String? = null,
            val description: String,
            val expected: List<Expected>,
            val sut: (LiveVideoDetail) -> List<LinkAnnotationRange>,
        ) {
            companion object {
                fun url(name: String, description: String, expected: List<Expected>): TestParam =
                    TestParam("url:$name", description, expected) { it.descriptionUrlAnnotation }

                fun url(name: String, description: String, expected: Expected): TestParam =
                    url(name, description, listOf(expected))

                fun hashtag(
                    name: String,
                    description: String,
                    expected: List<Expected>
                ): TestParam = TestParam("hashtag:$name", description, expected) {
                    it.descriptionHashTagAnnotation
                }

                fun hashtag(name: String, description: String, expected: Expected): TestParam =
                    hashtag(name, description, listOf(expected))
            }

            data class Expected(
                private val startPosition: Int,
                val text: String,
                val url: String = text,
                val tag: String,
            ) {
                val range: IntRange = startPosition until (startPosition + text.length)

                companion object {
                    fun url(startPosition: Int, text: String, url: String = text): Expected =
                        Expected(startPosition, text, url, tag = "URL")

                    fun hashtag(startPosition: Int, text: String): Expected {
                        val url = "https://twitter.com/search?q=%23${text.substring(1)}"
                        return Expected(startPosition, text, url, tag = "hashtag")
                    }
                }
            }

            override fun toString(): String = name ?: description.lines().first()
        }
    }
}
