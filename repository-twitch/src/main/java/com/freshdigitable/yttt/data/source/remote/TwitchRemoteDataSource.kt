package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService.Companion.getMe
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchRemoteDataSource @Inject constructor(
    private val helix: TwitchHelixService,
    private val ioScope: IoScope,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchDataSource.Remote {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<TwitchUserDetail>> =
        fetch { getUser(id = ids) }.map { it.body()?.data ?: emptyList() }

    override suspend fun fetchMe(): Result<TwitchUserDetail?> = fetch { getMe() }
        .map { it.body()?.data?.firstOrNull() }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<TwitchFollowings> =
        fetchAll { getFollowing(userId = userId, itemsPerPage = 100, cursor = it) }.map {
            val fetchedAt = dateTimeProvider.now()
            TwitchFollowings.createAtFetched(userId, it, fetchedAt)
        }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<TwitchStreams?> {
        val id = me ?: fetchMe().getOrNull()?.id ?: return Result.success(null)
        return fetchAll { getFollowedStreams(id, cursor = it) }.map {
            TwitchStreams.createAtFetched(id, it, dateTimeProvider.now())
        }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): Result<TwitchChannelSchedule?> = fetchAll(maxCount) {
        getChannelStreamSchedule(broadcasterId = id, cursor = it)
    }.map { res ->
        if (res.isEmpty()) {
            null
        } else {
            ChannelStreamSchedule(
                segments = res.mapNotNull { it.segments }.flatten(),
                broadcasterId = res.first().broadcasterId,
                broadcasterName = res.first().broadcasterName,
                broadcasterLogin = res.first().broadcasterLogin,
                vacation = res.first().vacation,
            )
        }
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        fetch { getGame(id) }.map { it.body()?.data?.toList() ?: emptyList() }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int
    ): Result<List<TwitchVideoDetail>> = fetch {
        getVideoByUserId(userId = id, itemsPerPage = itemCount)
    }.map { it.body()?.data?.toList() ?: emptyList() }

    private suspend inline fun <T> fetch(crossinline task: TwitchHelixService.() -> Call<T>): Result<Response<T>> =
        ioScope.asResult { helix.task().execute() }.mapCatching {
            if (!it.isSuccessful) {
                throw TwitchException(it.errorBody()?.string(), it.code())
            } else {
                it
            }
        }

    private suspend inline fun <E, P : Pageable<E>> fetchAll(
        maxCount: Int? = null,
        crossinline call: TwitchHelixService.(String?) -> Call<P>,
    ): Result<List<E>> = ioScope.asResult {
        var cursor: String? = null
        buildList {
            do {
                val response = helix.call(cursor).execute()
                val body = if (response.isSuccessful) {
                    response.body() ?: break
                } else {
                    throw TwitchException(response.errorBody()?.string(), response.code())
                }
                addAll(body.getItems())
                cursor = body.pagination.cursor
            } while (cursor != null && (maxCount == null || maxCount < size))
        }
    }
}

internal class TwitchException(
    override val message: String?,
    override val statusCode: Int,
) : IoScope.NetworkException(null)
