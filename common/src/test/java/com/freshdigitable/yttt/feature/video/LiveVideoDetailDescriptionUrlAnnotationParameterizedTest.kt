package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideoDetail
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
                actual = emptyList(),
            ),
            TestParam.UrlTest.create(
                name = "has trailing slash",
                description = "https://example.com/",
                TestParam.Actual(0, "https://example.com/"),
            ),
            TestParam.UrlTest.create(
                name = "no trailing slash",
                description = "https://example.com",
                TestParam.Actual(0, "https://example.com"),
            ),
            TestParam.UrlTest.create(
                name = "has caption",
                description = "link - https://example.com/",
                TestParam.Actual(7, "https://example.com/"),
            ),
            TestParam.UrlTest.create(
                name = "multibyte and line feed",
                description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                TestParam.Actual(7 + 7, "https://example.com"),
            ),
            TestParam.UrlTest(
                name = "1 line contains 2 urls",
                description = """18:00 https://example.com/@account1 https://example.com/@account2
                    |19:00 https://example.com/@account3 https://example.com/@account4
                    |""".trimMargin(),
                actual = listOf(
                    TestParam.Actual(6, "https://example.com/@account1"),
                    TestParam.Actual(36, "https://example.com/@account2"),
                    TestParam.Actual(66 + 6, "https://example.com/@account3"),
                    TestParam.Actual(66 + 36, "https://example.com/@account4"),
                ),
            ),
            TestParam.UrlTest.create(
                name = "trailing unexpected parentheses",
                description = "illust.: account00 (https://example.com/account00)",
                TestParam.Actual(20, "https://example.com/account00")
            ),
            TestParam.UrlTest(
                name = "no schema (well-known url: youtube.com)",
                description = """goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join""".trimMargin(),
                actual = listOf(
                    TestParam.Actual(8, "https://example.com/goods"),
                    TestParam.Actual(
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
                actual = listOf(
                    TestParam.Actual(8, "https://example.com/goods"),
                    TestParam.Actual(34 + 13, text = "https://www.youtube.com/@akihito104/join"),
                ),
            ),
            TestParam.UrlTest(
                name = "item order with no schema (well-known url: youtube.com)",
                description = """goods - https://example.com/goods
                    |membership - www.youtube.com/@akihito104/join
                    |privacy policy - https://example.com/privacy
                    |back number - https://youtube.com/playlist?list=example01
                    |""".trimMargin(),
                actual = listOf(
                    TestParam.Actual(8, "https://example.com/goods"),
                    TestParam.Actual(
                        34 + 13,
                        text = "www.youtube.com/@akihito104/join",
                        url = "https://www.youtube.com/@akihito104/join",
                    ),
                    TestParam.Actual(34 + 46 + 17, "https://example.com/privacy"),
                    TestParam.Actual(
                        34 + 46 + 45 + 14,
                        "https://youtube.com/playlist?list=example01"
                    ),
                ),
            ),
            TestParam.HashTagTest(
                name = "empty description",
                description = "",
                actual = emptyList(),
            ),
            TestParam.HashTagTest.create(
                name = "no line feed",
                description = "fun art: #hashtag",
                TestParam.Actual(9, "#hashtag"),
            ),
            TestParam.HashTagTest.create(
                name = "has line feed",
                description = """|
                    |fun art: #hashtag
                    |""".trimMargin(),
                TestParam.Actual(10, "#hashtag"),
            ),
            TestParam.HashTagTest(
                name = "multiple hashtags",
                description = """#hashtag1
                    |#hashtag2 #hashtag3
                    |""".trimMargin(),
                actual = listOf(
                    TestParam.Actual(0, "#hashtag1"),
                    TestParam.Actual(10, "#hashtag2"),
                    TestParam.Actual(10 + 10, "#hashtag3"),
                ),
            ),
            TestParam.HashTagTest(
                name = "multibyte (ja)",
                description = """配信タグ: #ハッシュタグ
                    |ファンアート: ＃全角ハッシュタグ　＃全角ハッシュタグR18
                    |本日のタグ：【#全角ハッシュタグコラボ】
                    |""".trimMargin(),
                actual = listOf(
                    TestParam.Actual(6, "#ハッシュタグ"),
                    TestParam.Actual(14 + 8, "＃全角ハッシュタグ"),
                    TestParam.Actual(14 + 18, "＃全角ハッシュタグR18"),
                    TestParam.Actual(14 + 31 + 7, "#全角ハッシュタグコラボ"),
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
        assertEquals(
            param.actual.map {
                LinkAnnotationRange(
                    range = it.startPosition until (it.startPosition + it.text.length),
                    url = it.url,
                    text = it.text,
                    tag = param.tag,
                )
            },
            actual,
        )
    }

    sealed class TestParam(
        private val name: String? = null,
        val description: String,
        val actual: List<Actual>,
        val tag: String,
        val sut: (LiveVideoDetail) -> List<LinkAnnotationRange>,
    ) {
        class UrlTest(
            name: String?,
            description: String,
            actual: List<Actual>,
        ) : TestParam(
            "url:$name",
            description,
            actual,
            tag = "URL",
            sut = { it.descriptionUrlAnnotation },
        ) {
            companion object {
                fun create(name: String? = null, description: String, actual: Actual): TestParam =
                    UrlTest(name, description, listOf(actual))
            }
        }

        class HashTagTest(
            name: String?,
            description: String,
            actual: List<Actual>,
        ) : TestParam(
            "hashTag:$name",
            description,
            actual.map {
                Actual(
                    it.startPosition,
                    it.text,
                    "https://twitter.com/search?q=%23${it.text.substring(1)}",
                )
            },
            tag = "hashtag",
            sut = { it.descriptionHashTagAnnotation },
        ) {
            companion object {
                fun create(name: String? = null, description: String, actual: Actual): TestParam =
                    HashTagTest(name, description, listOf(actual))
            }
        }

        data class Actual(
            val startPosition: Int,
            val text: String,
            val url: String = text,
        )

        override fun toString(): String = name ?: description.lines().first()
    }
}
