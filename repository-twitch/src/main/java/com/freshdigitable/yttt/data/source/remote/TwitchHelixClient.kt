package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.NetworkResponse.Companion.map
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService.Companion.getMe
import retrofit2.Call
import java.time.Duration

interface TwitchHelixClient {
    suspend fun getFollowing(
        userId: TwitchUser.Id,
        broadcasterId: TwitchUser.Id? = null,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): NetworkResponse<List<TwitchBroadcaster>>

    suspend fun getFollowedStreams(
        me: TwitchUser.Id,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): NetworkResponse<List<TwitchStream>>

    suspend fun getGame(id: Set<TwitchCategory.Id>): NetworkResponse<List<TwitchCategory>>
    suspend fun getChannelStreamSchedule(
        id: TwitchUser.Id,
        segmentId: TwitchChannelSchedule.Stream.Id? = null,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): NetworkResponse<TwitchChannelSchedule>

    suspend fun getMe(): NetworkResponse<TwitchUserDetail?>
    suspend fun getVideoByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): NetworkResponse<List<TwitchVideoDetail>>

    suspend fun getUser(ids: Set<TwitchUser.Id>?): NetworkResponse<List<TwitchUserDetail>>

    companion object {
        internal fun create(service: TwitchHelixService): TwitchHelixClient = Impl(service)
    }
}

class TwitchException(
    override val statusCode: Int,
    override val message: String?,
    override val cacheControl: CacheControl = CacheControl.EMPTY,
) : NetworkResponse.Exception(null) {
    override val isQuotaExceeded: Boolean
        get() = statusCode == 429 // Too Many Requests
}

private class Impl(
    private val service: TwitchHelixService,
) : TwitchHelixClient {
    override suspend fun getFollowing(
        userId: TwitchUser.Id,
        broadcasterId: TwitchUser.Id?,
        itemsPerPage: Int?,
        cursor: String?,
    ): NetworkResponse<List<TwitchBroadcaster>> = service.fetch {
        getFollowing(
            userId = userId,
            broadcasterId = broadcasterId,
            itemsPerPage = itemsPerPage,
            cursor = cursor,
        )
    }

    override suspend fun getGame(id: Set<TwitchCategory.Id>): NetworkResponse<List<TwitchCategory>> =
        service.fetch { getGame(id) }

    override suspend fun getFollowedStreams(
        me: TwitchUser.Id,
        itemsPerPage: Int?,
        cursor: String?,
    ): NetworkResponse<List<TwitchStream>> = service.fetch {
        getFollowedStreams(userId = me, itemsPerPage = itemsPerPage, cursor = cursor)
    }

    override suspend fun getChannelStreamSchedule(
        id: TwitchUser.Id,
        segmentId: TwitchChannelSchedule.Stream.Id?,
        itemsPerPage: Int?,
        cursor: String?,
    ): NetworkResponse<TwitchChannelSchedule> = service.fetch {
        getChannelStreamSchedule(
            broadcasterId = id,
            segmentId = segmentId,
            itemsPerPage = itemsPerPage,
            cursor = cursor,
        )
    }

    override suspend fun getMe(): NetworkResponse<TwitchUserDetail?> =
        service.fetch { getMe() }.map { it.firstOrNull() }

    override suspend fun getVideoByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): NetworkResponse<List<TwitchVideoDetail>> = service.fetch {
        getVideoByUserId(userId = id, itemsPerPage = itemCount)
    }

    override suspend fun getUser(ids: Set<TwitchUser.Id>?): NetworkResponse<List<TwitchUserDetail>> =
        service.fetch { getUser(id = ids) }

    companion object {
        private val MAX_AGE_DEFAULT = Duration.ofMinutes(5)

        private inline fun <T : HasItem<E>, E> TwitchHelixService.fetch(
            query: TwitchHelixService.() -> Call<T>,
        ): NetworkResponse<E> {
            val response = query().execute()
            val fetchedAt = response.headers().getDate("date")?.toInstant()
            val cacheControl = CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)
            if (response.isSuccessful) {
                val item = checkNotNull(response.body())
                return NetworkResponse.create(
                    item = item.item,
                    cacheControl = cacheControl,
                    nextPageToken = (item as? Pageable<*>)?.pagination?.cursor,
                )
            } else {
                throw TwitchException(response.code(), response.errorBody()?.string(), cacheControl)
            }
        }
    }
}
