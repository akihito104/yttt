package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRepository @Inject constructor() {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://id.twitch.tv/")
        .build()

    suspend fun login(): String = withContext(Dispatchers.IO) {
        val response = retrofit.create(TwitchOauth::class.java).authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
        ).execute()
        response.raw().request().url().toString()
    }

    private var token: TwitchOauthToken? = null
    fun putToken(token: TwitchOauthToken) {
        this.token = token
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchLiveRepository::class.simpleName
    }
}

interface TwitchOauth {
    @GET("oauth2/authorize?response_type=token")
    fun authorizeImplicitly(
        @Query("client_id") clientId: String,
        @Query("force_verify") forceVerify: Boolean? = null,
        @Query("redirect_uri") redirectUri: String,
        @Query("scope") scope: String,
        @Query("state") state: String = UUID.randomUUID().toString(),
    ): Call<ResponseBody>
}

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object
}
