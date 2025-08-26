package com.freshdigitable.yttt.data.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class AnnotatedStringTest : ShouldSpec({
    withData(
        mapOf(
            "empty description" to TestParam(
                description = "",
                expected = emptyList(),
            ),
            "has trailing slash" to TestParam(
                description = "https://example.com/",
                expected = listOf(TestParam.Expected.url(0, "https://example.com/")),
            ),
            "no trailing slash" to TestParam(
                description = "https://example.com",
                expected = listOf(TestParam.Expected.url(0, "https://example.com")),
            ),
            "has caption" to TestParam(
                description = "link - https://example.com/",
                expected = listOf(TestParam.Expected.url(7, "https://example.com/")),
            ),
            "multibyte and line feed" to TestParam(
                description = """こんにちは✨
                    |link - https://example.com
                    |""".trimMargin(),
                expected = listOf(TestParam.Expected.url(7 + 7, "https://example.com")),
            ),
            "1 line contains 2 urls" to TestParam(
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
            "trailing unexpected parentheses" to TestParam(
                description = """illust.: account00 (https://example.com/account00)
                        |mix: account01 【https://example.com/account01】
                    """.trimMargin(),
                expected = listOf(
                    TestParam.Expected.url(20, "https://example.com/account00"),
                    TestParam.Expected.url(51 + 16, "https://example.com/account01"),
                )
            ),
            "no schema (well-known url: youtube.com)" to TestParam(
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
            "has schema (well-known url: youtube.com)" to TestParam(
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
            "item order with no schema (well-known url: youtube.com)" to TestParam(
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
            "no line feed" to TestParam(
                description = "fun art: #hashtag",
                expected = listOf(TestParam.Expected.hashtag(9, "#hashtag")),
            ),
            "has line feed" to TestParam(
                description = """|
                    |fun art: #hashtag
                    |""".trimMargin(),
                expected = listOf(TestParam.Expected.hashtag(10, "#hashtag")),
            ),
            "multiple hashtags" to TestParam(
                description = """#hashtag1
                    |#hashtag2 #hashtag3
                    |""".trimMargin(),
                expected = listOf(
                    TestParam.Expected.hashtag(0, "#hashtag1"),
                    TestParam.Expected.hashtag(10, "#hashtag2"),
                    TestParam.Expected.hashtag(10 + 10, "#hashtag3"),
                ),
            ),
            "multibyte (ja)" to TestParam(
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
            "simple" to TestParam(
                description = "@account01",
                expected = listOf(TestParam.Expected.account(0, "@account01")),
            ),
            "multiple at line" to TestParam(
                description = """@account01
                        |@account02 @account03
                    """.trimMargin(),
                expected = listOf(
                    TestParam.Expected.account(0, "@account01"),
                    TestParam.Expected.account(11, "@account02"),
                    TestParam.Expected.account(11 + 11, "@account03"),
                ),
            ),
            "account in url" to TestParam(
                // should remove in post process
                description = """@account01
                        |https://www.youtube.com/@account02/join
                    """.trimMargin(),
                expected = listOf(
                    TestParam.Expected.account(0, "@account01"),
                    TestParam.Expected.url(0 + 11, "https://www.youtube.com/@account02/join"),
                ),
            ),
            "account with parenthesis" to TestParam(
                description = """account01 (@account01)
                        |account02 【@account02】
                    """.trimMargin(),
                expected = listOf(
                    TestParam.Expected.account(11, "@account01"),
                    TestParam.Expected.account(23 + 11, "@account02"),
                ),
            ),
            "hashtag and multibyte separator" to TestParam(
                description = """#ハッシュタグ￤@account_01
                        |〖#歌枠/#karaoke〗
                        |#hashtag┊description""".trimMargin(),
                expected = listOf(
                    TestParam.Expected.hashtag(0, "#ハッシュタグ"),
                    TestParam.Expected.account(0 + 8, "@account_01"),
                    TestParam.Expected.hashtag(20 + 1, "#歌枠"),
                    TestParam.Expected.hashtag(20 + 1 + 4, "#karaoke"),
                    TestParam.Expected.hashtag(35, "#hashtag"),
                ),
            ),
            "removable youtube url duplicated range" to TestParam(
                description = """https://gaming.youtube.com/channel/user-channel-id001""".trimMargin(),
                expected = listOf(
                    TestParam.Expected.url(
                        0,
                        "https://gaming.youtube.com/channel/user-channel-id001",
                    ),
                ),
            ),
            "hashtag as number" to TestParam(
                description = """〖#1〗title
                        |(＃2) subtitle""".trimMargin(),
                expected = emptyList(),
            ),
        )
    ) { (description, expected) ->
        // exercise
        val actual = AnnotatableString.create(description) {
            listOf("https://example.com/$it")
        }
        // assertion
        actual.annotationRangeItems.size shouldBe expected.size
        expected.forEachIndexed { i, e ->
            val a = actual.annotationRangeItems[i]
            a.range shouldBe e.range
            a.text shouldBe e.text
            a.url shouldBe e.url
        }
    }
})

internal data class TestParam(
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
}
