package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.LinkAnnotationRange.Url.Companion.ellipsize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LinkAnnotationRangeTest : ShouldSpec({
    context("fundamental") {
        should("serializableUrl") {
            val sut = LinkAnnotationRange.Url(range = 0..10, text = "https://example.com/")
            val actual = LinkAnnotationRange.createFromTag(sut.tag)
            actual shouldBe sut
        }

        should("serializableAccount") {
            val sut = LinkAnnotationRange.Account(
                range = 0..10,
                text = "@account01",
                urlCandidate = listOf("https://example.com/@account01"),
            )
            val actual = LinkAnnotationRange.createFromTag(sut.tag)
            actual shouldBe sut
        }
    }

    context("ellipsize text") {
        data class EllipsizeTextTestParam(
            val text: String,
            val totalLength: Int = 40,
            val ellipsis: String = "...",
            val expected: String,
        )

        withData(
            nameFn = { "'${it.text}' to '${it.expected}' with length ${it.totalLength} and ellipsis '${it.ellipsis}'" },
            EllipsizeTextTestParam(
                text = "https://example.com/",
                expected = "https://example.com/",
            ),
            EllipsizeTextTestParam(
                text = "https://example.com/very/long/but/ellips",
                expected = "https://example.com/very/long/but/ellips",
            ),
            EllipsizeTextTestParam(
                text = "https://example.com/very/long/so/ellipize",
                expected = "https://example.com/very/long/so/elli...",
            ),
            EllipsizeTextTestParam(
                text = "https://example.com/very/long/so/ellipized/this",
                ellipsis = "…",
                expected = "https://example.com/very/long/so/ellipi…",
            ),
        ) { param ->
            // setup
            val sut = LinkAnnotationRange.Url(
                text = param.text,
                range = 0 until param.text.length, // do not care
            )
            // exercise
            val actual = sut.ellipsize(param.totalLength, param.ellipsis).text
            // verify
            actual shouldBe param.expected
        }
    }
})
