package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable.Companion.update
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchFollowings.Companion.update
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchStreams.Companion.update
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchUserDetail.Companion.update
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.model.Updatable.Companion.isFresh
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
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<TwitchUserDetail>> {
        if (ids == null) {
            val me = fetchMe()
            return me.map { listOfNotNull(it) }
        }
        val cacheRes = localDataSource.findUsersById(ids)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = cacheRes.getOrDefault(emptyList())
        val remoteIds = ids - cache.filter { it.profileImageUrl.isNotEmpty() }.map { it.id }.toSet()
        if (remoteIds.isEmpty()) {
            return cacheRes
        }
        return remoteDataSource.findUsersById(remoteIds)
            .map { u -> u.map { it.update(TwitchUserDetail.MAX_AGE_USER_DETAIL) } }
            .onSuccess { localDataSource.addUsers(it) }
            .map { it + cache }
    }

    override suspend fun fetchMe(): Result<TwitchUserDetail?> {
        val me = localDataSource.fetchMe()
        if (me.isSuccess && me.getOrNull() != null) {
            return me
        }
        return remoteDataSource.fetchMe()
            .map { m -> m?.update(TwitchUserDetail.MAX_AGE_USER_DETAIL) }
            .onSuccess { if (it != null) localDataSource.setMe(it) }
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<TwitchFollowings> {
        val cacheRes = localDataSource.fetchAllFollowings(userId)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = checkNotNull(cacheRes.getOrNull())
        if (cache.isFresh(dateTimeProvider.now())) {
            return cacheRes
        }
        return remoteDataSource.fetchAllFollowings(userId)
            .onSuccess { localDataSource.replaceAllFollowings(it) }
            .map { cache.update(it) }
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<TwitchStreams?> {
        val id = me ?: fetchMe().getOrNull()?.id ?: return Result.success(null)
        val cacheRes = localDataSource.fetchFollowedStreams(id)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = checkNotNull(cacheRes.getOrNull())
        if (cache.isFresh(dateTimeProvider.now())) {
            return cacheRes
        }
        return remoteDataSource.fetchFollowedStreams(id)
            .map { cache.update(checkNotNull(it)) }
    }

    override suspend fun replaceFollowedStreams(followedStreams: TwitchStreams.Updated) {
        localDataSource.replaceFollowedStreams(followedStreams)
    }

    override suspend fun removeStreamScheduleById(id: Set<TwitchChannelSchedule.Stream.Id>) {
        localDataSource.removeStreamScheduleById(id)
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): Result<TwitchChannelScheduleUpdatable> {
        val cache = localDataSource.fetchFollowedStreamSchedule(id)
        val current = dateTimeProvider.now()
        if (cache.isSuccess && checkNotNull(cache.getOrNull()).isFresh(current)) {
            return cache
        }
        return remoteDataSource.fetchFollowedStreamSchedule(id, maxCount).onSuccess {
            val updatable = it.update(TwitchChannelScheduleUpdatable.MAX_AGE_CHANNEL_SCHEDULE)
            localDataSource.setFollowedStreamSchedule(id, updatable)
        }
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> {
        val cacheRes = localDataSource.fetchCategory(id)
            .map { c -> c.filter { it.artUrlBase != null } }
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = cacheRes.getOrDefault(emptyList())
        val remoteIds = id - cache.map { it.id }.toSet()
        if (remoteIds.isEmpty()) {
            return cacheRes
        }
        return remoteDataSource.fetchCategory(remoteIds)
            .onSuccess { localDataSource.upsertCategory(it) }
            .map { cache + it }
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<TwitchVideoDetail>> = remoteDataSource.fetchVideosByUserId(id, itemCount)

    override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>) {
        localDataSource.cleanUpByUserId(ids)
    }

    suspend fun deleteAllTables() {
        localDataSource.deleteAllTables()
    }
}

@Singleton
class TwitchLiveRepository @Inject constructor(localSource: TwitchLiveDataSource.Local) :
    TwitchLiveDataSource by localSource
