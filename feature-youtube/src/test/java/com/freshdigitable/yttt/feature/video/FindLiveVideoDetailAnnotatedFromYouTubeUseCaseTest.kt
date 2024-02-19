package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.MockkResponseRule
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Enclosed::class)
class FindLiveVideoDetailAnnotatedFromYouTubeUseCaseTest {

    class Standard {
        private val responseRule = MockkResponseRule()

        @get:Rule
        val rule: RuleChain = RuleChain.outerRule(MockKRule(this))
            .around(responseRule)

        @MockK
        internal lateinit var useCase: FindLiveVideoFromYouTubeUseCase

        @Test
        fun testNotFound(): Unit = runBlocking {
            // setup
            responseRule.run {
                addMocks(useCase)
                useCase.apply uc@{
                    coRegister { this@uc.invoke(any()) } returns null
                }
            }
            val sut = FindLiveVideoDetailAnnotatedFromYouTubeUseCase(useCase)
            // exercise
            val actual = sut(LiveVideo.Id(value = "test01", type = YouTubeVideo.Id::class))
            // verify
            assertNotNull(sut)
            assertNull(actual)
        }
    }

    @RunWith(Parameterized::class)
    class DescriptionAnnotationParameterizedTest(val param: Param) {
        private val responseRule = MockkResponseRule()

        @get:Rule
        val rule: RuleChain = RuleChain.outerRule(MockKRule(this))
            .around(responseRule)

        @MockK
        internal lateinit var useCase: FindLiveVideoFromYouTubeUseCase
        private val sut: FindLiveVideoDetailAnnotatedFromYouTubeUseCase by lazy {
            FindLiveVideoDetailAnnotatedFromYouTubeUseCase(useCase)
        }

        companion object {
            @JvmStatic
            @Parameters(name = "{0}")
            fun params(): List<Param> = listOf(
                Param(
                    name = "url",
                    description = "https://example.com/",
                    actual = listOf(Param.Actual.url(0, "https://example.com/"))
                ),
                Param(
                    name = "hashtag",
                    description = "#hashtag1",
                    actual = listOf(Param.Actual.hashtag(0, "#hashtag1"))
                ),
                Param(
                    name = "hashtag and url",
                    description = """#hashtag1
                        |https://example.com/
                        |main: #hash_main
                        |fa: #hash_fa
                    """.trimMargin(),
                    actual = listOf(
                        Param.Actual.hashtag(0, "#hashtag1"),
                        Param.Actual.url(10, "https://example.com/"),
                        Param.Actual.hashtag(10 + 21 + 6, "#hash_main"),
                        Param.Actual.hashtag(10 + 21 + 17 + 4, "#hash_fa"),
                    )
                ),
                Param(
                    name = "url has anchor",
                    description = """#hashtag1
                        |https://example.com/#example
                    """.trimMargin(),
                    actual = listOf(
                        Param.Actual.hashtag(0, "#hashtag1"),
                        Param.Actual.url(10, "https://example.com/#example"),
                    )
                ),
            )
        }

        @Test
        fun test(): Unit = runBlocking {
            responseRule.run {
                addMocks(useCase)
                useCase.apply uc@{
                    coRegister { this@uc.invoke(any()) } returns mockk<LiveVideoDetail>().apply {
                        every { description } returns param.description
                    }
                }
            }
            // exercise
            val actual = sut.invoke(LiveVideo.Id(value = "test01", type = YouTubeVideo.Id::class))
            // verify
            assertNotNull(actual)
            checkNotNull(actual)
            assertEquals(param.actual.size, actual.descriptionAnnotationRangeItems.size)
            assertEquals(
                param.actual.map {
                    LinkAnnotationRange(
                        range = it.startPosition until (it.startPosition + it.text.length),
                        url = it.url,
                        text = it.text,
                        tag = it.tag,
                    )
                },
                actual.descriptionAnnotationRangeItems,
            )
        }

        class Param(
            val name: String,
            val description: String,
            val actual: List<Actual>,
        ) {
            class Actual(
                val startPosition: Int,
                val text: String,
                val url: String = text,
                val tag: String,
            ) {
                companion object {
                    fun url(startPosition: Int, text: String, url: String = text): Actual =
                        Actual(startPosition, text, url, tag = "URL")

                    fun hashtag(startPosition: Int, text: String): Actual = Actual(
                        startPosition,
                        text,
                        url = "https://twitter.com/search?q=%23${text.substring(1)}",
                        tag = "hashtag",
                    )
                }
            }

            override fun toString(): String = name
        }
    }
}
