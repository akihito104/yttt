package com.freshdigitable.yttt.widget

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.feature.video.FetchPinnedVideoUseCase
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class YtttStateTest : ShouldSpec(
    {
        isolationMode = IsolationMode.InstancePerTest
        val videoId = MutableStateFlow<String?>(null)
        val initialUpdateVideoIdIfNeed: (YouTubeVideo.Id) -> Unit = { videoId.value = it.value }

        val fetchPinnedVideo = mockk<FetchPinnedVideoUseCase>()
        val pinnedFlow = MutableStateFlow<List<YouTubeVideoExtended>>(emptyList())
        every { fetchPinnedVideo() } returns pinnedFlow

        val sut = YtttState(
            videoId = videoId,
            initialUpdateVideoIdIfNeed = initialUpdateVideoIdIfNeed,
            fetchPinnedVideo = fetchPinnedVideo,
        )

        context("no pinned item") {
            should("pinnedItem is null when both videoId and pinned are null/empty") {
                videoId.value = null
                sut.pinnedItem.first() shouldBe null
            }

            should("pinnedItem is null when pinned is empty") {
                videoId.value = "video1"
                sut.pinnedItem.first() shouldBe null
            }
        }

        context("pinned has only one item") {
            val video1 = mockk<YouTubeVideoExtended> {
                every { id } returns YouTubeVideo.Id("video1")
            }
            pinnedFlow.value = listOf(video1)
            every { fetchPinnedVideo() } returns pinnedFlow

            should("prevItem and nextItem are null") {
                videoId.value = "video1"
                sut.prevItem.first() shouldBe null
                sut.nextItem.first() shouldBe null
            }
        }

        context("pinned has 3 items") {
            val video1 = mockk<YouTubeVideoExtended> {
                every { id } returns YouTubeVideo.Id("video1")
            }
            val video2 = mockk<YouTubeVideoExtended> {
                every { id } returns YouTubeVideo.Id("video2")
            }
            val video3 = mockk<YouTubeVideoExtended> {
                every { id } returns YouTubeVideo.Id("video3")
            }
            pinnedFlow.value = listOf(video1, video2, video3)
            every { fetchPinnedVideo() } returns pinnedFlow

            should("pinnedItem is first item and initialUpdateVideoIdIfNeed is called when videoId is null") {
                videoId.value = null
                val actual = sut.pinnedItem.first()
                actual shouldBe video1
                videoId.value shouldBe video1.id.value
            }

            should("pinnedItem is matching item when videoId matches") {
                videoId.value = "video2"
                val actual = sut.pinnedItem.first()
                actual shouldBe video2
            }

            should("pinnedItem is next item when videoId does not match exactly") {
                videoId.value = "video0"
                val actual = sut.pinnedItem.first()
                actual shouldBe video1

                videoId.value = "video1.5"
                val actual2 = sut.pinnedItem.first()
                actual2 shouldBe video2
            }

            should("pinnedItem is first item when videoId is greater than all") {
                videoId.value = "video4"
                val actual = sut.pinnedItem.first()
                actual shouldBe video1
            }

            should("prevItem and nextItem return correct ids") {
                videoId.value = "video2"
                sut.prevItem.first() shouldBe video1.id
                sut.nextItem.first() shouldBe video3.id

                videoId.value = "video1"
                sut.prevItem.first() shouldBe video3.id
                sut.nextItem.first() shouldBe video2.id

                videoId.value = "video3"
                sut.prevItem.first() shouldBe video2.id
                sut.nextItem.first() shouldBe video1.id
            }
        }
    },
)
