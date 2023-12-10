package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService.Companion.getMe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Call
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLiveRemoteDataSource @Inject constructor(
    private val oauth: TwitchOauthService,
    private val helix: TwitchHelixService,
) : TwitchLiveDataSource.Remote {
    override suspend fun getAuthorizeUrl(): String = withContext(Dispatchers.IO) {
        val response = oauth.authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
        ).execute()
        response.raw().request.url.toString()
    }

    private suspend fun <T> fetch(task: suspend TwitchHelixService.() -> T): T =
        withContext(Dispatchers.IO) { helix.task() }

    private suspend fun <E, P : Pageable<E>> fetchAll(
        maxCount: Int? = null,
        call: TwitchHelixService.(String?) -> Call<P>,
    ): List<E> = fetch {
        var cursor: String? = null
        val items = mutableListOf<E>()
        do {
            val response = helix.call(cursor).execute()
            val body = response.body() ?: break
            items.addAll(body.getItems())
            cursor = body.pagination.cursor

        } while (cursor != null && (maxCount == null || maxCount < items.size))
        items
    }

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> =
        fetch {
            val response = getUser(id = ids?.map { it.value }).execute()
            response.body()?.data ?: return@fetch emptyList()
        }

    override suspend fun fetchMe(): TwitchUserDetail? = fetch {
        val response = getMe().execute()
        response.body()?.data?.firstOrNull()
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        return fetchAll { getFollowing(userId = userId.value, itemsPerPage = 100, cursor = it) }
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): List<TwitchStream> {
        val id = me ?: fetchMe()?.id ?: return emptyList()
        return fetchAll { getFollowedStreams(id.value, cursor = it) }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): List<TwitchChannelSchedule> = fetchAll(maxCount) {
        getChannelStreamSchedule(broadcasterId = id.value, cursor = it)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int
    ): List<TwitchVideoDetail> {
        val resp = fetch { getVideoByUserId(userId = id.value, itemsPerPage = itemCount).execute() }
        return resp.body()?.data?.toList() ?: emptyList()
    }

    override val onAir: Flow<List<TwitchStream>> get() = throw AssertionError()
    override val upcoming: Flow<List<TwitchChannelSchedule>> get() = throw AssertionError()
    override suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<TwitchVideo.TwitchVideoId> =
        throw AssertionError()
}
