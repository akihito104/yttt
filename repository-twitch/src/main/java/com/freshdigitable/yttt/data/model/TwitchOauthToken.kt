package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.BuildConfig

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object {
        private const val PARAM_TOKEN_TYPE = "token_type"
        private const val PARAM_STATE = "state"
        private const val PARAM_ACCESS_TOKEN = "access_token"
        private const val PARAM_SCOPE = "scope"
        private val params = setOf(PARAM_TOKEN_TYPE, PARAM_STATE, PARAM_ACCESS_TOKEN, PARAM_SCOPE)

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
                tokenType = requireNotNull(values[PARAM_TOKEN_TYPE]),
                state = requireNotNull(values[PARAM_STATE]),
                accessToken = requireNotNull(values[PARAM_ACCESS_TOKEN]),
                scope = requireNotNull(values[PARAM_SCOPE]),
            )
        }
    }
}
