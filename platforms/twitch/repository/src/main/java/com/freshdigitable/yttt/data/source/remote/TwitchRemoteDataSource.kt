package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.flattenToList
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.recoverFromNotFoundError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchRemoteDataSource @Inject constructor(
    private val helix: TwitchHelixClient,
    private val ioScope: IoScope,
) : TwitchDataSource.Remote {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<Updatable<TwitchUserDetail>>> =
        fetch { getUser(ids = ids) }.map { it.flattenToList() }

    override suspend fun fetchMe(): Result<Updatable<TwitchUserDetail>?> = fetch { getMe() }.map {
        it.item?.toUpdatable(it.cacheControl)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<Updatable<TwitchFollowings>> =
        fetchAll { getFollowing(userId = userId, itemsPerPage = 100, cursor = it) }.map { res ->
            TwitchFollowings.create(userId, res.item, res.cacheControl)
        }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<Updatable<TwitchStreams>?> {
        val id = me ?: fetchMe().getOrNull()?.item?.id ?: return Result.success(null)
        return fetchAll { getFollowedStreams(id, cursor = it) }.map { res ->
            TwitchStreams.create(id, res.item, res.cacheControl)
        }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): Result<Updatable<TwitchChannelSchedule?>> = ioScope.asResult {
        var cacheControl: CacheControl? = null
        buildList {
            var cursor: String? = null
            var count = 0
            do {
                val itemsPerPage = (maxCount - count).coerceAtMost(CHANNEL_STREAM_SCHEDULE_PAGE_SIZE)
                val res = helix.getChannelStreamSchedule(
                    id = id,
                    itemsPerPage = itemsPerPage,
                    cursor = cursor,
                )
                count += (res.item.segments?.size ?: 0)
                add(res.item)
                cacheControl = res.cacheControl
                cursor = res.nextPageToken
            } while (cursor != null && count < maxCount)
        }.toUpdatable(cacheControl ?: CacheControl.EMPTY)
    }.map { updatable ->
        val schedule: TwitchChannelSchedule? = object : TwitchChannelSchedule {
            override val segments: List<TwitchChannelSchedule.Stream>
                get() = updatable.item.mapNotNull { it.segments }.flatten()
            override val broadcaster: TwitchUser get() = updatable.item.first().broadcaster
            override val vacation: TwitchChannelSchedule.Vacation? get() = updatable.item.first().vacation
        }
        schedule.toUpdatable(updatable.cacheControl)
    }.recoverFromNotFoundError { cacheControl ->
        // 404 Not Found: The broadcaster has not created a streaming schedule.
        Updatable.create(item = null, cacheControl = cacheControl)
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        fetch { getGame(id) }.map { it.item }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<Updatable<TwitchVideoDetail>>> = fetch {
        getVideoByUserId(id = id, itemCount = itemCount)
    }.map { it.flattenToList() }

    private suspend inline fun <T> fetch(
        crossinline task: suspend TwitchHelixClient.() -> NetworkResponse<T>,
    ): Result<NetworkResponse<T>> = ioScope.asResult { helix.task() }

    private suspend inline fun <E> fetchAll(
        maxCount: Int? = null,
        crossinline call: suspend TwitchHelixClient.(String?) -> NetworkResponse<List<E>>,
    ): Result<Updatable<List<E>>> = ioScope.asResult {
        var cursor: String? = null
        var cacheControl: CacheControl? = null
        buildList {
            do {
                val body = helix.call(cursor)
                cacheControl = body.cacheControl
                addAll(body.item)
                cursor = body.nextPageToken
            } while (cursor != null && (maxCount == null || maxCount < size))
        }.toUpdatable(cacheControl ?: CacheControl.EMPTY)
    }

    companion object {
        private const val CHANNEL_STREAM_SCHEDULE_PAGE_SIZE = 25
    }
}
