package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import kotlinx.coroutines.flow.Flow

interface TwitchLiveDataSource {
    val onAir: Flow<List<TwitchStream>>
    val upcoming: Flow<List<TwitchChannelSchedule>>
    suspend fun getAuthorizeUrl(): String
    suspend fun findUsersById(ids: Collection<TwitchUser.Id>? = null): List<TwitchUserDetail>
    suspend fun fetchMe(): TwitchUserDetail?
    suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster>
    suspend fun fetchFollowedStreams(): List<TwitchStream>
    suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int = 10,
    ): List<TwitchChannelSchedule>

    suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<out TwitchVideo.TwitchVideoId>?
    suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int = 20,
    ): List<TwitchVideoDetail>
}
