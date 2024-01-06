package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.BuildConfig

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object {
        private const val paramTokenType = "token_type"
        private const val paramState = "state"
        private const val paramAccessToken = "access_token"
        private const val paramScope = "scope"
        private val params = setOf(paramTokenType, paramState, paramAccessToken, paramScope)

        fun create(url: String): TwitchOauthToken {
            check(url.startsWith(BuildConfig.TWITCH_REDIRECT_URI)) { "unsupported url" }

            val queryString = url.split("#").last()
            println(queryString)
            val query = queryString.split("&")
            println(query)
            val values = params.associateWith { p ->
                query.first { q -> q.startsWith("$p=") }.split("=").last()
            }
            return TwitchOauthToken(
                tokenType = requireNotNull(values[paramTokenType]),
                state = requireNotNull(values[paramState]),
                accessToken = requireNotNull(values[paramAccessToken]),
                scope = requireNotNull(values[paramScope]),
            )
        }
    }
}
