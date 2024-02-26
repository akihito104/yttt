package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.feature.video.LinkAnnotationRange.Companion.ellipsizeTextIfNeeded
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionHashTagAnnotation
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionUrlAnnotation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LiveVideoDetailDescriptionUrlAnnotationParameterizedTest(
    private val param: TestParam,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params(): List<TestParam> = listOf(
            TestParam.UrlTest(
                name = "empty description",
                description = "",
                expected = emptyList(),
            ),
            TestParam.UrlTest.create(
                name = "has trailing slash",
                description = "https://example.com/",
                TestParam.Expected.url(0, "https://example.com/"),
            ),
            TestParam.UrlTest.create(
                name = "no trailing slash",
                description = "https://example.com",
                TestParam.Expected.url(0, "https://example.com"),
            ),
            TestParam.UrlTest.create(
                name = "has caption",
                description = "link - https://example.com/",
                TestParam.Expected.url(7, "https://example.com/"),
            ),
            TestParam.UrlTest.create(
                name = "multibyte and line feed",
                description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                TestParam.Expected.url(7 + 7, "https://example.com"),
            ),
            TestParam.UrlTest(
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
            TestParam.UrlTest.create(
                name = "trailing unexpected parentheses",
                description = "illust.: account00 (https://example.com/account00)",
                TestParam.Expected.url(20, "https://example.com/account00")
            ),
            TestParam.UrlTest(
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
            TestParam.UrlTest(
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
            TestParam.UrlTest(
                name = "item order with no schema (well-known url: youtube.com)",
                description = """goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join
                    |back number 1 - https://youtube.com/playlist?list=exampl
                    |back number 2 - https://youtube.com/playlist?list=example
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
                        "https://youtube.com/playlist?list=exampl",
                    ),
                    TestParam.Expected.url(
                        34 + 46 + 57 + 16,
                        "https://youtube.com/playlist?list=example",
                        ellipsized = "https://youtube.com/playlist?list=exa...",
                    ),
                ),
            ),
            TestParam.HashTagTest(
                name = "empty description",
                description = "",
                expected = emptyList(),
            ),
            TestParam.HashTagTest.create(
                name = "no line feed",
                description = "fun art: #hashtag",
                TestParam.Expected.hashtag(9, "#hashtag"),
            ),
            TestParam.HashTagTest.create(
                name = "has line feed",
                description = """|
                    |fun art: #hashtag
                    |""".trimMargin(),
                TestParam.Expected.hashtag(10, "#hashtag"),
            ),
            TestParam.HashTagTest(
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
            TestParam.HashTagTest(
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
        val expected = param.expected.associateWith {
            LinkAnnotationRange(
                range = it.startPosition until (it.startPosition + it.text.length),
                url = it.url,
                text = it.text,
                tag = it.tag,
            )
        }
        assertEquals(expected.values.toList(), actual)
        expected.forEach { (e, range) ->
            assertEquals(e.ellipsized, range.ellipsizeTextIfNeeded())
        }
    }

    sealed class TestParam(
        private val name: String? = null,
        val description: String,
        val expected: List<Expected>,
        val sut: (LiveVideoDetail) -> List<LinkAnnotationRange>,
    ) {
        class UrlTest(name: String, description: String, expected: List<Expected>) : TestParam(
            "url:$name", description, expected, sut = { it.descriptionUrlAnnotation },
        ) {
            companion object {
                fun create(name: String, description: String, expected: Expected): TestParam =
                    UrlTest(name, description, listOf(expected))
            }
        }

        class HashTagTest(name: String, description: String, expected: List<Expected>) : TestParam(
            "hashtag:$name", description, expected, sut = { it.descriptionHashTagAnnotation },
        ) {
            companion object {
                fun create(name: String, description: String, expected: Expected): TestParam =
                    HashTagTest(name, description, listOf(expected))
            }
        }

        data class Expected(
            val startPosition: Int,
            val text: String,
            val url: String = text,
            val tag: String,
            val ellipsized: String = text,
        ) {
            companion object {
                fun url(
                    startPosition: Int,
                    text: String,
                    url: String = text,
                    ellipsized: String = text
                ): Expected = Expected(startPosition, text, url, tag = "URL", ellipsized)

                fun hashtag(startPosition: Int, text: String): Expected {
                    val url = "https://twitter.com/search?q=%23${text.substring(1)}"
                    return Expected(startPosition, text, url, tag = "hashtag")
                }
            }
        }

        override fun toString(): String = name ?: description.lines().first()
    }
}
