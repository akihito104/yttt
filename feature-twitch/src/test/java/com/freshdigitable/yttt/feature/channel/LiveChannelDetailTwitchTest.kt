package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.model.TwitchUserDetail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId

class LiveChannelDetailTwitchTest : ShouldSpec({
    should("statsText returns formatted string with loginName and publishedAt") {
        // setup
        val sut = LiveChannelDetailTwitch(
            detail = mockk<TwitchUserDetail>().apply {
                every { loginName } returns "channel"
                every { createdAt } returns Instant.parse("2022-11-04T14:22:02+09:00")
            },
            zoneId = ZoneId.of("Asia/Tokyo"),
        )
        // exercise
        val actual = sut.statsText
        // verify
        actual shouldBe "channel・Published:2022/11/04"
    }
})
