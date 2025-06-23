package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.TwitchDataSource
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchRemoteDataSource @Inject constructor(
    private val helix: TwitchHelixClient,
    private val ioScope: IoScope,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchDataSource.Remote {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<TwitchUserDetail>> =
        fetch { getUser(ids = ids) }.map { u ->
            val now = dateTimeProvider.now()
            val cacheControl = CacheControl.create(now, MAX_AGE_DEFAULT)
            u.map { TwitchUserDetailImpl(it, cacheControl) }
        }

    override suspend fun fetchMe(): Result<TwitchUserDetail?> = fetch { getMe() }.map {
        if (it == null) return@map null
        val now = dateTimeProvider.now()
        val cacheControl = CacheControl.create(now, MAX_AGE_DEFAULT)
        TwitchUserDetailImpl(it, cacheControl)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<TwitchFollowings> =
        fetchAll { getFollowing(userId = userId, itemsPerPage = 100, cursor = it) }.map {
            val cacheControl = CacheControl.create(dateTimeProvider.now(), MAX_AGE_DEFAULT)
            TwitchFollowings.create(userId, it, cacheControl)
        }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<TwitchStreams?> {
        val id = me ?: fetchMe().getOrNull()?.id ?: return Result.success(null)
        return fetchAll { getFollowedStreams(id, cursor = it) }.map {
            val cacheControl = CacheControl.create(dateTimeProvider.now(), MAX_AGE_DEFAULT)
            TwitchStreams.create(id, it, cacheControl)
        }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): Result<TwitchChannelScheduleUpdatable> = ioScope.asResult {
        buildList {
            var cursor: String? = null
            var count = 0
            do {
                val itemsPerPage = (maxCount - count).coerceAtMost(25)
                val res = helix.getChannelStreamSchedule(
                    id = id,
                    itemsPerPage = itemsPerPage,
                    cursor = cursor,
                )
                count += (res.item.segments?.size ?: 0)
                add(res.item)
                cursor = res.nextPageToken
            } while (cursor != null && count < maxCount)
        }
    }.map { res ->
        val schedule = object : TwitchChannelSchedule {
            override val segments: List<TwitchChannelSchedule.Stream>
                get() = res.mapNotNull { it.segments }.flatten()
            override val broadcaster: TwitchUser get() = res.first().broadcaster
            override val vacation: TwitchChannelSchedule.Vacation? get() = res.first().vacation
        }
        TwitchChannelScheduleUpdatable.create(
            schedule = schedule,
            cacheControl = CacheControl.create(dateTimeProvider.now(), MAX_AGE_DEFAULT)
        )
    }.recoverCatching {
        if (it is TwitchException && it.statusCode == 404) {
            // 404 Not Found: The broadcaster has not created a streaming schedule.
            TwitchChannelScheduleUpdatable.create(
                schedule = null,
                cacheControl = CacheControl.create(dateTimeProvider.now(), MAX_AGE_DEFAULT)
            )
        } else {
            throw it
        }
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        fetch { getGame(id) }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<TwitchVideoDetail>> = fetch { getVideoByUserId(id = id, itemCount = itemCount) }

    private suspend inline fun <T> fetch(crossinline task: suspend TwitchHelixClient.() -> NetworkResponse<T>): Result<T> =
        ioScope.asResult { helix.task().item }

    private suspend inline fun <E> fetchAll(
        maxCount: Int? = null,
        crossinline call: suspend TwitchHelixClient.(String?) -> NetworkResponse<List<E>>,
    ): Result<List<E>> = ioScope.asResult {
        var cursor: String? = null
        buildList {
            do {
                val body = helix.call(cursor)
                addAll(body.item)
                cursor = body.nextPageToken
            } while (cursor != null && (maxCount == null || maxCount < size))
        }
    }

    companion object {
        private val MAX_AGE_DEFAULT = Duration.ofMinutes(5)
    }
}

internal class TwitchUserDetailImpl(
    detail: TwitchUserDetail,
    override val cacheControl: CacheControl,
) : TwitchUserDetail by detail
