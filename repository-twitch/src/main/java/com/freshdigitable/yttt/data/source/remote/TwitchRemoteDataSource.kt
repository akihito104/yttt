package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService.Companion.getMe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Call
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchRemoteDataSource @Inject constructor(
    private val oauth: TwitchOauthService,
    private val helix: TwitchHelixService,
    private val ioDispatcher: CoroutineDispatcher,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchDataSource.Remote {
    override suspend fun getAuthorizeUrl(state: String): String = withContext(ioDispatcher) {
        val response = oauth.authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
            state = state,
        ).execute()
        response.raw().request.url.toString()
    }

    private suspend inline fun <T> fetch(crossinline task: TwitchHelixService.() -> T): T =
        withContext(ioDispatcher) { helix.task() }

    private suspend inline fun <E, P : Pageable<E>> fetchAll(
        maxCount: Int? = null,
        crossinline call: TwitchHelixService.(String?) -> Call<P>,
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

    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): List<TwitchUserDetail> =
        fetch {
            val response = getUser(id = ids?.map { it.value }).execute()
            response.body()?.data ?: return@fetch emptyList()
        }

    override suspend fun fetchMe(): TwitchUserDetail? = fetch {
        val response = getMe().execute()
        response.body()?.data?.firstOrNull()
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings {
        val items =
            fetchAll { getFollowing(userId = userId.value, itemsPerPage = 100, cursor = it) }
        val fetchedAt = dateTimeProvider.now()
        return TwitchFollowings.createAtFetched(userId, items, fetchedAt)
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): TwitchStreams? {
        val id = me ?: fetchMe()?.id ?: return null
        val s = fetchAll { getFollowedStreams(id.value, cursor = it) }
        return TwitchStreams.createAtFetched(id, s, dateTimeProvider.now())
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): List<TwitchChannelSchedule> = fetchAll(maxCount) {
        getChannelStreamSchedule(broadcasterId = id.value, cursor = it)
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> {
        val res = fetch { getGame(id).execute() }
        return res.body()?.data?.toList() ?: emptyList()
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int
    ): List<TwitchVideoDetail> {
        val resp = fetch { getVideoByUserId(userId = id.value, itemsPerPage = itemCount).execute() }
        return resp.body()?.data?.toList() ?: emptyList()
    }
}
