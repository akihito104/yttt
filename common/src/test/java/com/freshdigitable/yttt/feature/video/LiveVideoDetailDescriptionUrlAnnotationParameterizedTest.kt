package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideoDetail
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
            TestParam.create(
                tag = "has trailing slash",
                description = "https://example.com/",
                TestParam.Actual(0, "https://example.com/"),
            ),
            TestParam.create(
                tag = "no trailing slash",
                description = "https://example.com",
                TestParam.Actual(0, "https://example.com"),
            ),
            TestParam.create(
                tag = "has caption",
                description = "link - https://example.com/",
                TestParam.Actual(7, "https://example.com/"),
            ),
            TestParam.create(
                tag = "multibyte and line feed",
                description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                TestParam.Actual(7 + 7, "https://example.com"),
            ),
            TestParam(
                tag = "1 line contains 2 urls",
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
            TestParam.create(
                tag = "trailing unexpected parentheses",
                description = "illust.: account00 (https://example.com/account00)",
                TestParam.Actual(20, "https://example.com/account00")
            ),
            TestParam(
                tag = "no schema (well-known url: youtube.com)",
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
            TestParam(
                tag = "item order with no schema (well-known url: youtube.com)",
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
        )
    }

    @Test
    fun test() {
        // setup
        val detail = mockk<LiveVideoDetail>().apply {
            every { description } returns param.description
        }
        // exercise
        val actual = detail.descriptionUrlAnnotation
        // assertion
        assertEquals(
            param.actual.map {
                LinkAnnotationRange(
                    range = it.startPosition until (it.startPosition + it.text.length),
                    url = it.url,
                    text = it.text,
                )
            },
            actual,
        )
    }

    data class TestParam(
        val tag: String? = null,
        val description: String,
        val actual: List<Actual>,
    ) {
        companion object {
            fun create(tag: String? = null, description: String, actual: Actual): TestParam =
                TestParam(tag, description, listOf(actual))
        }

        data class Actual(
            val startPosition: Int,
            val text: String,
            val url: String = text,
        )

        override fun toString(): String = tag ?: description.lines().first()
    }
}
