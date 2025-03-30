package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchFollowings.Companion.update
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchStreams.Companion.update
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchRepository @Inject constructor(
    private val remoteDataSource: TwitchDataSource.Remote,
    private val localDataSource: TwitchDataSource.Local,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchDataSource, ImageDataSource by localDataSource {
    override suspend fun getAuthorizeUrl(state: String): String =
        remoteDataSource.getAuthorizeUrl(state)

    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            val me = checkNotNull(fetchMe())
            return listOf(me)
        }
        val cache = localDataSource.findUsersById(ids)
        val remoteIds = ids - cache.filter { it.profileImageUrl.isNotEmpty() }.map { it.id }.toSet()
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

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings {
        val cache = localDataSource.fetchAllFollowings(userId)
        if (dateTimeProvider.now() < cache.updatableAt) {
            return cache
        }
        val remote = remoteDataSource.fetchAllFollowings(userId)
        localDataSource.replaceAllFollowings(remote)
        return cache.update(remote)
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): TwitchStreams? {
        val id = me ?: fetchMe()?.id ?: return null
        val cache = checkNotNull(localDataSource.fetchFollowedStreams(id))
        if (dateTimeProvider.now() < cache.updatableAt) {
            return cache
        }
        val res = checkNotNull(remoteDataSource.fetchFollowedStreams(id))
        return cache.update(res)
    }

    override suspend fun replaceFollowedStreams(followedStreams: TwitchStreams.Updated) {
        localDataSource.replaceFollowedStreams(followedStreams)
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

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = remoteDataSource.fetchVideosByUserId(id, itemCount)

    override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>) {
        localDataSource.cleanUpByUserId(ids)
    }

    suspend fun deleteAllTables() {
        localDataSource.deleteAllTables()
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchRepository::class.simpleName
    }
}

@Singleton
class TwitchLiveRepository @Inject constructor(localSource: TwitchLiveDataSource.Local) :
    TwitchLiveDataSource by localSource
