package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId

class LiveChannelDetailYouTubeTest : ShouldSpec({
    should("SubscriberCountIsNotHidden") {
        val detailMock: YouTubeChannelDetail = mockk {
            every { customUrl } returns "@channel"
            every { isSubscriberHidden } returns false
            every { publishedAt } returns Instant.parse("2021-02-23T00:00:00.000+09:00")
        }

        withData(
            nameFn = { "subscriber: ${it.subscriberCount.first}, video: ${it.videoCount.first}, views: ${it.viewsCount.first}" },
            SubscriberCountIsNotHiddenParams.params()
        ) { param ->
            val sut = LiveChannelDetailYouTube(
                detail = detailMock.apply {
                    every { subscriberCount } returns BigInteger.valueOf(param.subscriberCount.first)
                    every { videoCount } returns BigInteger.valueOf(param.videoCount.first)
                    every { viewsCount } returns BigInteger.valueOf(param.viewsCount.first)
                },
                zoneId = ZoneId.of("Asia/Tokyo"),
            )
            val actual = sut.statsText
            actual shouldBe "@channel・" +
                "Followers:${param.subscriberCount.second}・Videos:${param.videoCount.second}・Views:${param.viewsCount.second}" +
                "・Published:2021/02/23"
        }
    }

    should("SubscriberCountIsHidden") {
        val detailMock: YouTubeChannelDetail = mockk {
            every { customUrl } returns "@channel"
            every { isSubscriberHidden } returns true
            every { publishedAt } returns Instant.parse("2021-02-23T00:00:00.000+09:00")
        }

        withData(
            nameFn = { "video: ${it.videoCount.first}, views: ${it.viewsCount.first}" },
            SubscriberCountIsHiddenParams.params()
        ) { param ->
            val sut = LiveChannelDetailYouTube(
                detail = detailMock.apply {
                    every { videoCount } returns BigInteger.valueOf(param.videoCount.first)
                    every { viewsCount } returns BigInteger.valueOf(param.viewsCount.first)
                },
                zoneId = ZoneId.of("Asia/Tokyo"),
            )
            val actual = sut.statsText
            actual shouldBe "@channel・Videos:${param.videoCount.second}・Views:${param.viewsCount.second}・Published:2021/02/23"
        }
    }
})

internal object SubscriberCountIsNotHiddenParams {
    fun params(): List<Param> = listOf(
        Param(
            subscriberCount = 100L to "100",
            videoCount = 120L to "120",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 100L to "100",
            videoCount = 120L to "120",
            viewsCount = 78900L to "78,900",
        ),
        Param(
            subscriberCount = 100L to "100",
            videoCount = 120L to "120",
            viewsCount = 789_012_345L to "789,012,345",
        ),
        Param(
            subscriberCount = 100L to "100",
            videoCount = 2340L to "2,340",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 100L to "100",
            videoCount = 23456000L to "23,456,000",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 1230L to "1.23k",
            videoCount = 120L to "120",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 12300L to "12.3k",
            videoCount = 120L to "120",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 123000L to "123k",
            videoCount = 120L to "120",
            viewsCount = 130L to "130",
        ),
        Param(
            subscriberCount = 123000000L to "123M",
            videoCount = 1205L to "1,205",
            viewsCount = 1_430_698_029L to "1,430,698,029",
        ),
    )

    data class Param(
        val subscriberCount: Pair<Long, String>,
        val videoCount: Pair<Long, String>,
        val viewsCount: Pair<Long, String>,
    )
}

internal object SubscriberCountIsHiddenParams {
    fun params(): List<Param> = listOf(
        Param(
            videoCount = 120L to "120",
            viewsCount = 130L to "130",
        ),
        Param(
            videoCount = 120L to "120",
            viewsCount = 78900L to "78,900",
        ),
        Param(
            videoCount = 120L to "120",
            viewsCount = 789_012_345L to "789,012,345",
        ),
        Param(
            videoCount = 2340L to "2,340",
            viewsCount = 130L to "130",
        ),
        Param(
            videoCount = 23456000L to "23,456,000",
            viewsCount = 130L to "130",
        ),
        Param(
            videoCount = 1205L to "1,205",
            viewsCount = 1_430_698_029L to "1,430,698,029",
        ),
    )

    data class Param(
        val videoCount: Pair<Long, String>,
        val viewsCount: Pair<Long, String>,
    )
}
