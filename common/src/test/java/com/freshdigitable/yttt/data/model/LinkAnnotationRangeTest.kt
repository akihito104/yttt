package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.LinkAnnotationRange.Url.Companion.ellipsize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class LinkAnnotationRangeTest {
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
}
