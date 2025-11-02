package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchFollowings.Companion.update
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchStreams.Companion.update
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchUserDetail.Companion.update
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.isFresh
import com.freshdigitable.yttt.data.model.Updatable.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.TwitchDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchRepository @Inject constructor(
    private val remoteDataSource: TwitchDataSource.Remote,
    private val localDataSource: TwitchDataSource.Local,
    private val extendedDataSource: TwitchDataSource.Extended,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchDataSource, TwitchDataSource.Extended by extendedDataSource, ImageDataSource by localDataSource {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<Updatable<TwitchUserDetail>>> {
        if (ids == null) {
            val me = fetchMe()
            return me.map { listOfNotNull(it) }
        }
        return localDataSource.findUsersById(ids).mapCatching { cache ->
            val current = dateTimeProvider.now()
            val c = cache.filter { it.isFresh(current) }
                .filter { it.item.profileImageUrl.isNotEmpty() }
            val remoteIds = ids - c.map { it.item.id }
            if (remoteIds.isEmpty()) {
                cache
            } else {
                remoteDataSource.findUsersById(remoteIds)
                    .map { u -> u.map { it.update(TwitchUserDetail.MAX_AGE_USER_DETAIL) } }
                    .onSuccess { localDataSource.addUsers(it) }
                    .getOrThrow() + c
            }
        }
    }

    override suspend fun fetchMe(): Result<Updatable<TwitchUserDetail>?> {
        val me = localDataSource.fetchMe()
        if (me.isSuccess && me.getOrNull() != null) {
            return me
        }
        return remoteDataSource.fetchMe()
            .map { m -> m?.update(TwitchUserDetail.MAX_AGE_USER_DETAIL) }
            .onSuccess { if (it != null) localDataSource.setMe(it) }
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<Updatable<TwitchFollowings>> =
        localDataSource.fetchAllFollowings(userId).mapCatching { cache ->
            if (cache.isFresh(dateTimeProvider.now())) {
                cache
            } else {
                remoteDataSource.fetchAllFollowings(userId)
                    .onSuccess { localDataSource.replaceAllFollowings(it) }
                    .map { cache.update(it) }
                    .getOrThrow()
            }
        }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<Updatable<TwitchStreams>?> {
        val id = me ?: fetchMe().getOrNull()?.item?.id ?: return Result.success(null)
        return localDataSource.fetchFollowedStreams(id).mapCatching { cache ->
            checkNotNull(cache)
            if (cache.isFresh(dateTimeProvider.now())) {
                cache
            } else {
                remoteDataSource.fetchFollowedStreams(id)
                    .map { cache.update(checkNotNull(it)) }
                    .getOrThrow()
            }
        }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): Result<Updatable<TwitchChannelSchedule?>> {
        val cache = localDataSource.fetchFollowedStreamSchedule(id)
        val current = dateTimeProvider.now()
        if (cache.isSuccess && checkNotNull(cache.getOrNull()).isFresh(current)) {
            return cache
        }
        return remoteDataSource.fetchFollowedStreamSchedule(id, maxCount)
            .map { it.overrideMaxAge(TwitchChannelSchedule.MAX_AGE_CHANNEL_SCHEDULE) }
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        localDataSource.fetchCategory(id).mapCatching { cache ->
            val remoteIds = id - cache.filter { it.artUrlBase != null }.map { it.id }.toSet()
            if (remoteIds.isEmpty()) {
                cache
            } else {
                remoteDataSource.fetchCategory(remoteIds)
                    .onSuccess { localDataSource.upsertCategory(it) }
                    .getOrThrow() + cache
            }
        }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<Updatable<TwitchVideoDetail>>> =
        remoteDataSource.fetchVideosByUserId(id, itemCount)
}
