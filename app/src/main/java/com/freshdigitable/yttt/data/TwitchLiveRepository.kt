package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.local.TwitchLiveLocalDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchLiveRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRepository @Inject constructor(
    private val remoteDataSource: TwitchLiveRemoteDataSource,
    private val localDataSource: TwitchLiveLocalDataSource,
) : TwitchLiveDataSource {
    override val onAir: Flow<List<LiveVideo>> = localDataSource.onAir
    override val upcoming: Flow<List<LiveVideo>> = localDataSource.upcoming

    override suspend fun getAuthorizeUrl(): String = remoteDataSource.getAuthorizeUrl()

    override suspend fun findUsersById(
        ids: Collection<LiveChannel.Id>?,
    ): List<LiveChannelDetail> {
        if (ids == null) {
            val me = checkNotNull(fetchMe())
            return listOf(me)
        }
        val cache = localDataSource.findUsersById(ids)
        if (cache.size == ids.size) {
            return cache
        }
        val remoteIds = ids - cache.map { it.id }.toSet()
        val remote = remoteDataSource.findUsersById(remoteIds)
        localDataSource.addUsers(remote)
        return cache + remote
    }

    override suspend fun fetchMe(): LiveChannelDetail? {
        val me = localDataSource.fetchMe()
        if (me != null) {
            return me
        }
        val res = remoteDataSource.findUsersById().firstOrNull()
        localDataSource.setMe(res)
        return res
    }

    override suspend fun fetchAllFollowings(
        userId: LiveChannel.Id,
    ): List<LiveSubscription> {
        val cache = localDataSource.fetchAllFollowings(userId)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteDataSource.fetchAllFollowings(userId)
        val users = findUsersById(remote.map { it.channel.id })
        val res = remote.map { r ->
            val u = users.first { it.id == r.channel.id }
            object : LiveSubscription by r {
                override val channel: LiveChannel get() = u
            }
        }
        localDataSource.addFollowings(userId, res)
        return res
    }

    override suspend fun fetchFollowedStreams(): List<LiveVideo> {
        val me = fetchMe() ?: return emptyList()
        val res = remoteDataSource.fetchFollowedStreams(me)
        localDataSource.addFollowedStreams(res)
        return res
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: LiveChannel.Id,
        maxCount: Int,
    ): List<LiveVideo> {
        val res = remoteDataSource.fetchFollowedStreamSchedule(id, maxCount)
        localDataSource.updateFollowedStreamSchedule(id, res)
        return res
    }

    override suspend fun fetchStreamDetail(id: LiveVideo.Id): LiveVideo {
        check(id.platform == LivePlatform.TWITCH)
        return localDataSource.fetchStreamDetail(id)
    }

    override suspend fun fetchVideosByChannelId(
        id: LiveChannel.Id,
        itemCount: Int,
    ): List<LiveVideo> = remoteDataSource.fetchVideosByChannelId(id, itemCount)

    companion object {
        @Suppress("unused")
        private val TAG = TwitchLiveRepository::class.simpleName
    }
}

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object
}
