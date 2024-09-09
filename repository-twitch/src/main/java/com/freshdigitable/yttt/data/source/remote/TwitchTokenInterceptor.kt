package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchTokenInterceptor @Inject constructor(
    private val accountDataSource: TwitchAccountDataStore.Local,
    private val coroutineScope: CoroutineScope,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.twitch.tv") {
            return chain.proceed(request)
        }
        if (!accountDataSource.hasValidToken()) {
            return chain.proceed(request)
        }
        val token = accountDataSource.getTwitchToken() ?: return chain.proceed(request)
        val req = request.newBuilder()
            .header(HEADER_AUTHORIZATION, toAuthorizationValue(token))
            .header("Client-Id", BuildConfig.TWITCH_CLIENT_ID)
            .build()
        val response = chain.proceed(req)
        // https://dev.twitch.tv/docs/authentication/#tokens-dont-last-forever
        // >> If a token becomes invalid, your API requests return HTTP status code 401 Unauthorized.
        if (response.code == 401) {
            coroutineScope.launch {
                accountDataSource.invalidateTwitchToken()
            }
        }
        return response
    }

    companion object {
        const val HEADER_AUTHORIZATION: String = "Authorization"
        fun toAuthorizationValue(token: String): String = "Bearer $token"
        private fun TwitchAccountDataStore.Local.hasValidToken(): Boolean =
            getTwitchToken() != null && !isTwitchTokenInvalidated()
    }
}
