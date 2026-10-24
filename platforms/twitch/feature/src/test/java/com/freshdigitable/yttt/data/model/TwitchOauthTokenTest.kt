package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.BuildConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TwitchOauthTokenTest : ShouldSpec(
    {
        should("create") {
            val sut = TwitchOauthToken.create(
                "${BuildConfig.TWITCH_REDIRECT_URI}/#access_token=accesstoken12345&scope=user%3Aread%3Afollows" +
                    "&state=e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3&token_type=bearer",
            )
            sut.accessToken shouldBe "accesstoken12345"
            sut.scope shouldBe "user%3Aread%3Afollows"
            sut.state shouldBe "e4b5a9a7-5ab3-4c7f-b603-62a3acfdc4c3"
            sut.tokenType shouldBe "bearer"
        }
    },
)
