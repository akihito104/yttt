package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import kotlinx.coroutines.flow.Flow

interface TwitchLiveDataSource {
    val onAir: Flow<List<TwitchStream>>
    val upcoming: Flow<List<TwitchChannelSchedule>>
    suspend fun getAuthorizeUrl(state: String): String
    suspend fun findUsersById(ids: Set<TwitchUser.Id>? = null): List<TwitchUserDetail>
    suspend fun fetchMe(): TwitchUserDetail?
    suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings
    suspend fun fetchFollowedStreams(me: TwitchUser.Id? = null): List<TwitchStream>
    suspend fun addFollowedStreams(followedStreams: Collection<TwitchStream>)
    suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int = 10,
    ): List<TwitchChannelSchedule>

    suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<out TwitchVideo.TwitchVideoId>?
    suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int = 20,
    ): List<TwitchVideoDetail>

    suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>)

    interface Local : TwitchLiveDataSource, ImageDataSource {
        suspend fun addUsers(users: Collection<TwitchUserDetail>)
        suspend fun setMe(me: TwitchUserDetail)
        suspend fun replaceAllFollowings(followings: TwitchFollowings)
        suspend fun setFollowedStreamSchedule(
            userId: TwitchUser.Id,
            schedule: Collection<TwitchChannelSchedule>,
        )

        suspend fun deleteAllTables()
        override suspend fun getAuthorizeUrl(state: String): String = throw NotImplementedError()
    }

    interface Remote : TwitchLiveDataSource {
        override val onAir: Flow<List<TwitchStream>> get() = throw NotImplementedError()
        override val upcoming: Flow<List<TwitchChannelSchedule>> get() = throw NotImplementedError()
        override suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<TwitchVideo.TwitchVideoId> =
            throw NotImplementedError()

        override suspend fun addFollowedStreams(followedStreams: Collection<TwitchStream>) =
            throw NotImplementedError()

        override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>): Unit =
            throw NotImplementedError()
    }
}
