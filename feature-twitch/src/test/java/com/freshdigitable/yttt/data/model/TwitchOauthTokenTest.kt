package com.freshdigitable.yttt.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchOauthTokenTest {
    @Test
    fun testCreate() {
        val sut = TwitchOauthToken.create(
            "https://twitch_login/#access_token=accesstoken12345&scope=user%3Aread%3Afollows&state=e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3&token_type=bearer"
        )
        assertEquals("accesstoken12345", sut.accessToken)
        assertEquals("user%3Aread%3Afollows", sut.scope)
        assertEquals("e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3", sut.state)
        assertEquals("bearer", sut.tokenType)
    }
}
