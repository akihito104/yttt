package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRepository @Inject constructor(
    private val remoteDataSource: TwitchLiveDataSource.Remote,
    private val localDataSource: TwitchLiveDataSource.Local,
    coroutineScope: CoroutineScope,
) : TwitchLiveDataSource, ImageDataSource by localDataSource {
    override val onAir: StateFlow<List<TwitchStream>> = localDataSource.onAir
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    override val upcoming: Flow<List<TwitchChannelSchedule>> = localDataSource.upcoming

    override suspend fun getAuthorizeUrl(state: String): String =
        remoteDataSource.getAuthorizeUrl(state)

    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            val me = checkNotNull(fetchMe())
            return listOf(me)
        }
        val cache = localDataSource.findUsersById(ids)
        val remoteIds = ids - cache.map { it.id }.toSet()
        if (remoteIds.isEmpty()) {
            return cache
        }
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

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): List<TwitchStream> {
        val id = me ?: fetchMe()?.id ?: return emptyList()
        val cache = localDataSource.fetchFollowedStreams()
        if (cache.isNotEmpty()) {
            return cache
        }
        val res = remoteDataSource.fetchFollowedStreams(id)
        return res
    }

    override suspend fun addFollowedStreams(followedStreams: Collection<TwitchStream>) {
        localDataSource.addFollowedStreams(followedStreams)
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
        localDataSource.setFollowedStreamSchedule(id, res)
        return res
    }

    override suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<out TwitchVideo.TwitchVideoId>? {
        return localDataSource.fetchStreamDetail(id)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = remoteDataSource.fetchVideosByUserId(id, itemCount)

    suspend fun deleteAllTables() {
        localDataSource.deleteAllTables()
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchLiveRepository::class.simpleName
    }
}
