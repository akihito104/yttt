package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.TwitchVideoDetail
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
    override val onAir: Flow<List<TwitchStream>> = localDataSource.onAir
    override val upcoming: Flow<List<TwitchChannelSchedule>> = localDataSource.upcoming

    override suspend fun getAuthorizeUrl(): String = remoteDataSource.getAuthorizeUrl()

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> {
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

    override suspend fun fetchMe(): TwitchUserDetail? {
        val me = localDataSource.fetchMe()
        if (me != null) {
            return me
        }
        val res = remoteDataSource.fetchMe() ?: return null
        localDataSource.setMe(res)
        return res
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        val cache = localDataSource.fetchAllFollowings(userId)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteDataSource.fetchAllFollowings(userId)
        localDataSource.replaceAllFollowings(userId, remote)
        return remote
    }

    override suspend fun fetchFollowedStreams(): List<TwitchStream> {
        val me = fetchMe() ?: return emptyList()
        val cache = localDataSource.fetchFollowedStreams()
        if (cache.isNotEmpty()) {
            return cache
        }
        val res = remoteDataSource.fetchFollowedStreams(me.id)
        localDataSource.addFollowedStreams(res)
        return res
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): List<TwitchChannelSchedule> {
        val cache = localDataSource.fetchFollowedStreamSchedule(id)
        if (cache.isNotEmpty()) {
            return cache
        }
        val res = remoteDataSource.fetchFollowedStreamSchedule(id, maxCount)
        localDataSource.setFollowedStreamSchedule(res)
        return res
    }

    override suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<out TwitchVideo.TwitchVideoId>? {
        check(id.platform == LivePlatform.TWITCH)
        return localDataSource.fetchStreamDetail(id)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = remoteDataSource.fetchVideosByUserId(id, itemCount)

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
