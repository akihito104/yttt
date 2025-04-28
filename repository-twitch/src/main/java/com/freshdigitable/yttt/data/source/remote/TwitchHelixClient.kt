package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService.Companion.getMe
import retrofit2.Call

interface TwitchHelixClient {
    suspend fun getFollowing(
        userId: TwitchUser.Id,
        broadcasterId: TwitchUser.Id? = null,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): Response<List<TwitchBroadcaster>>

    suspend fun getFollowedStreams(
        me: TwitchUser.Id,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): Response<List<TwitchStream>>

    suspend fun getGame(id: Set<TwitchCategory.Id>): Response<List<TwitchCategory>>
    suspend fun getChannelStreamSchedule(
        id: TwitchUser.Id,
        segmentId: TwitchChannelSchedule.Stream.Id? = null,
        itemsPerPage: Int? = null,
        cursor: String? = null,
    ): Response<TwitchChannelSchedule>

    suspend fun getMe(): Response<TwitchUserDetail?>
    suspend fun getVideoByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Response<List<TwitchVideoDetail>>

    suspend fun getUser(ids: Set<TwitchUser.Id>?): Response<List<TwitchUserDetail>>

    interface Response<T> {
        val item: T
        val nextPageToken: String? get() = null

        companion object
    }

    companion object {
        internal fun create(service: TwitchHelixService): TwitchHelixClient = Impl(service)
    }
}

class TwitchException(
    override val message: String?,
    override val statusCode: Int,
) : IoScope.NetworkException(null)

private class Impl(
    private val service: TwitchHelixService,
) : TwitchHelixClient {
    override suspend fun getFollowing(
        userId: TwitchUser.Id,
        broadcasterId: TwitchUser.Id?,
        itemsPerPage: Int?,
        cursor: String?,
    ): TwitchHelixClient.Response<List<TwitchBroadcaster>> = service.fetch {
        getFollowing(
            userId = userId,
            broadcasterId = broadcasterId,
            itemsPerPage = itemsPerPage,
            cursor = cursor,
        )
    }

    override suspend fun getGame(id: Set<TwitchCategory.Id>): TwitchHelixClient.Response<List<TwitchCategory>> =
        service.fetch { getGame(id) }

    override suspend fun getFollowedStreams(
        me: TwitchUser.Id,
        itemsPerPage: Int?,
        cursor: String?,
    ): TwitchHelixClient.Response<List<TwitchStream>> = service.fetch {
        getFollowedStreams(userId = me, itemsPerPage = itemsPerPage, cursor = cursor)
    }

    override suspend fun getChannelStreamSchedule(
        id: TwitchUser.Id,
        segmentId: TwitchChannelSchedule.Stream.Id?,
        itemsPerPage: Int?,
        cursor: String?,
    ): TwitchHelixClient.Response<TwitchChannelSchedule> = service.fetch {
        getChannelStreamSchedule(
            broadcasterId = id,
            segmentId = segmentId,
            itemsPerPage = itemsPerPage,
            cursor = cursor,
        )
    }

    override suspend fun getMe(): TwitchHelixClient.Response<TwitchUserDetail?> {
        val res = service.fetch { getMe() }
        return object : TwitchHelixClient.Response<TwitchUserDetail?> {
            override val item: TwitchUserDetail? = res.item.firstOrNull()
        }
    }

    override suspend fun getVideoByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): TwitchHelixClient.Response<List<TwitchVideoDetail>> = service.fetch {
        getVideoByUserId(userId = id, itemsPerPage = itemCount)
    }

    override suspend fun getUser(ids: Set<TwitchUser.Id>?): TwitchHelixClient.Response<List<TwitchUserDetail>> =
        service.fetch { getUser(id = ids) }

    companion object {
        private inline fun <T> TwitchHelixService.fetch(query: TwitchHelixService.() -> Call<T>): T {
            val response = query().execute()
            if (response.isSuccessful) {
                return checkNotNull(response.body())
            } else {
                throw TwitchException(response.errorBody()?.string(), response.code())
            }
        }
    }
}
