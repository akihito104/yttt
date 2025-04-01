package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveStream
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import kotlinx.coroutines.flow.Flow

interface TwitchDataSource {
    suspend fun getAuthorizeUrl(state: String): String
    suspend fun findUsersById(ids: Set<TwitchUser.Id>? = null): List<TwitchUserDetail>
    suspend fun fetchMe(): TwitchUserDetail?
    suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings
    suspend fun fetchFollowedStreams(me: TwitchUser.Id? = null): TwitchStreams?
    suspend fun replaceFollowedStreams(followedStreams: TwitchStreams.Updated)
    suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int = 10,
    ): List<TwitchChannelSchedule>

    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory>

    suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int = 20,
    ): List<TwitchVideoDetail>

    suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>)

    interface Local : TwitchDataSource, TwitchLiveDataSource.Local, ImageDataSource {
        suspend fun addUsers(users: Collection<TwitchUserDetail>)
        suspend fun setMe(me: TwitchUserDetail)
        suspend fun replaceAllFollowings(followings: TwitchFollowings)
        suspend fun setFollowedStreamSchedule(
            userId: TwitchUser.Id,
            schedule: Collection<TwitchChannelSchedule>,
        )

        suspend fun deleteAllTables()
        override suspend fun getAuthorizeUrl(state: String): String = throw NotImplementedError()
        suspend fun addCategory(category: Collection<TwitchCategory>)
    }

    interface Remote : TwitchDataSource {
        override suspend fun replaceFollowedStreams(followedStreams: TwitchStreams.Updated) =
            throw NotImplementedError()

        override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>): Unit =
            throw NotImplementedError()
    }
}

interface TwitchLiveDataSource {
    val onAir: Flow<List<TwitchLiveStream>>
    val upcoming: Flow<List<TwitchLiveChannelSchedule>>
    suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchLiveVideo<out TwitchVideo.TwitchVideoId>?
    interface Local : TwitchLiveDataSource
}
