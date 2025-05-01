package com.freshdigitable.yttt.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TwitchOauthTokenTest {
    @Test
    fun testCreate() {
        val sut = TwitchOauthToken.create(
            "https://twitch_login/#access_token=accesstoken12345&scope=user%3Aread%3Afollows&state=e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3&token_type=bearer"
        )
        assertThat(sut.accessToken).isEqualTo("accesstoken12345")
        assertThat(sut.scope).isEqualTo("user%3Aread%3Afollows")
        assertThat(sut.state).isEqualTo("e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3")
        assertThat(sut.tokenType).isEqualTo("bearer")
    }
}
