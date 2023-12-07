package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchTokenInterceptor @Inject constructor(
    private val accountDataSource: AccountLocalDataSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.twitch.tv") {
            return chain.proceed(request)
        }
        val token = accountDataSource.getTwitchToken() ?: return chain.proceed(request)
        val req = request.newBuilder()
            .header(HEADER_AUTHORIZATION, toAuthorizationValue(token))
            .header("Client-Id", BuildConfig.TWITCH_CLIENT_ID)
            .build()
        return chain.proceed(req)
    }

    companion object {
        const val HEADER_AUTHORIZATION: String = "Authorization"
        fun toAuthorizationValue(token: String): String = "Bearer $token"
    }
}
