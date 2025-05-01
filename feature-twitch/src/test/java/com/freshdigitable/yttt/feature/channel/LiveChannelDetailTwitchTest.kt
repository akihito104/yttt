package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LiveChannelDetailTwitchTest {
    @Test
    fun test() {
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
        assertThat(actual).isEqualTo("channel・Published:2022/11/04")
    }
}
