package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.TwitchOauthDataStore
import com.freshdigitable.yttt.data.source.TwitchOauthRemoteDataStore
import com.freshdigitable.yttt.data.source.remote.TwitchOauthService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchAccountRepository @Inject constructor(
    local: TwitchAccountDataStore.Local,
) : TwitchAccountDataStore by local, AccountRepository {
    override fun hasAccount(): Boolean = twitchToken.value != null
}

@Singleton
class TwitchOauthRepository @Inject constructor(
    private val localSource: TwitchOauthDataStore.Local,
    private val remoteSource: TwitchOauthRemoteDataStore,
) : TwitchOauthDataStore by localSource {
    override suspend fun getAuthorizeUrl(state: String): Result<String> =
        remoteSource.getAuthorizeUrl(state)
}

@Singleton
internal class TwitchAccountRemoteDataStoreImpl @Inject constructor(
    private val service: TwitchOauthService,
    private val ioScope: IoScope,
) : TwitchOauthRemoteDataStore {
    override suspend fun getAuthorizeUrl(state: String): Result<String> = ioScope.asResult {
        val response = service.authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
            state = state,
        ).execute()
        response.raw().request.url.toString()
    }
}
