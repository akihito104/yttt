package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.flow.Flow

interface TwitchLiveDataSource {
    val onAir: Flow<List<LiveVideo>>
    val upcoming: Flow<List<LiveVideo>>
    suspend fun getAuthorizeUrl(): String
    suspend fun findUsersById(ids: Collection<LiveChannel.Id>? = null): List<LiveChannelDetail>
    suspend fun fetchMe(): LiveChannelDetail?
    suspend fun fetchAllFollowings(userId: LiveChannel.Id): List<LiveSubscription>
    suspend fun fetchFollowedStreams(): List<LiveVideo>
    suspend fun fetchFollowedStreamSchedule(id: LiveChannel.Id, maxCount: Int = 10): List<LiveVideo>
    suspend fun fetchStreamDetail(id: LiveVideo.Id): LiveVideo
    suspend fun fetchVideosByChannelId(id: LiveChannel.Id, itemCount: Int = 20): List<LiveVideo>
}
