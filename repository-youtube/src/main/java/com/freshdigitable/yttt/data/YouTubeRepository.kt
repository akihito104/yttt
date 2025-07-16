package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.isFresh
import com.freshdigitable.yttt.data.model.Updatable.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.map
import com.freshdigitable.yttt.data.model.Updatable.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail.Companion.isFromAnotherChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.YouTubeLiveDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val remoteSource: YouTubeDataSource.Remote,
    private val localSource: YouTubeDataSource.Local,
    private val dateTimeProvider: DateTimeProvider,
) : YouTubeDataSource, YouTubeLiveDataSource by localSource {
    override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions>> {
        if (dateTimeProvider.now() < localSource.subscriptionsFetchedAt + SUBSCRIPTION_FETCH_PERIOD) {
            return localSource.fetchSubscriptions(pageSize)
        }
        return remoteSource.fetchSubscriptions(pageSize).map { r ->
            r.map {
                if (it.hasNextPage) it
                else YouTubeSubscriptions.Updated(localSource.fetchSubscriptionIds(), it)
            }
        }
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): Result<List<YouTubeChannelLog>> {
        val pa = if (publishedAfter != null) {
            publishedAfter
        } else {
            val cacheRes = localSource.fetchLiveChannelLogs(channelId, maxResult = 1)
            if (cacheRes.isFailure) {
                return cacheRes
            }
            val cache = cacheRes.getOrNull()?.firstOrNull()
            cache?.dateTime?.plusSeconds(1) ?: (dateTimeProvider.now() - ACTIVITY_MAX_PERIOD)
        }
        return remoteSource.fetchLiveChannelLogs(channelId, pa, maxResult).onSuccess {
            localSource.addLiveChannelLogs(it)
        }
    }

    fun removeImageByUrl(url: Collection<String>) {
        localSource.removeImageByUrl(url)
    }

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> {
        val cacheRes = localSource.fetchPlaylist(ids)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = checkNotNull(cacheRes.getOrNull())
        val needed = ids - cache.map { it.item.id }.toSet()
        if (needed.isEmpty()) {
            return cacheRes
        }
        return remoteSource.fetchPlaylist(needed).onSuccess {
            localSource.addPlaylist(it)
        }.map { it + cache }
    }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
        eTag: String?,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>> {
        val cache = localSource.fetchPlaylistWithItems(id, maxResult)
            .onFailure { return Result.failure(it) }
            .onSuccess { res ->
                if (res?.isFresh(dateTimeProvider.now()) == true) {
                    return Result.success(res)
                }
            }.getOrNull()
        return remoteSource.fetchPlaylistWithItems(id, maxResult, cache?.item)
            .onSuccess { u ->
                val uploadedAtAnotherChannel = u.item.items
                    .filter { it.isFromAnotherChannel }
                    .mapNotNull { it.videoOwnerChannelId }
                if (uploadedAtAnotherChannel.isNotEmpty()) {
                    fetchChannelList(uploadedAtAnotherChannel.toSet())
                }
                localSource.updatePlaylistWithItems(u)
            }
    }

    override suspend fun fetchPlaylistWithItemIds(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<YouTubePlaylistWithItem<*>> {
        val cache = localSource.fetchPlaylistWithItemIds(id, maxResult).getOrNull()
        return remoteSource.fetchPlaylistWithItems(id, maxResult, cache, cache?.eTag)
            .onSuccess { localSource.updatePlaylistWithItems(it) }
            .recoverCatching { throwable ->
                val t = throwable as? NetworkResponse.Exception
                if (t?.statusCode == 304) {
                    checkNotNull(cache).toUpdatable(t.cacheControl).also {
                        localSource.updatePlaylistWithItemsCacheControl(it)
                    }
                } else {
                    throw throwable
                }
            }
            .map { it.item }
    }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<Updatable<YouTubeChannelDetail>>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        val cacheRes = localSource.fetchChannelList(ids).map { res ->
            val current = dateTimeProvider.now()
            res.filter { it.isFresh(current) }
        }
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = cacheRes.getOrNull() ?: emptyList()
        val needed = ids - cache.map { it.item.id }.toSet()
        if (needed.isEmpty()) {
            return cacheRes
        }
        return remoteSource.fetchChannelList(needed)
            .map { c -> c.map { it.overrideMaxAge(YouTubeChannelDetail.MAX_AGE) } }
            .onSuccess { localSource.addChannelList(it) }
            .map { it + cache }
    }

    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> {
        val cacheRes = localSource.fetchChannelSection(id)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = checkNotNull(cacheRes.getOrNull())
        if (cache.isNotEmpty()) { // TODO: updatable
            return cacheRes
        }
        return remoteSource.fetchChannelSection(id).onSuccess {
            localSource.addChannelSection(it)
        }
    }

    companion object {
        private val ACTIVITY_MAX_PERIOD = Period.ofDays(7)
        private val SUBSCRIPTION_FETCH_PERIOD = Duration.ofHours(2)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        val cache = localSource.fetchVideoList(ids)
            .onFailure { return Result.failure(it) }
            .map { v -> v.associateBy { it.item.id } }
            .getOrDefault(emptyMap())
        val current = dateTimeProvider.now()
        val notCached = ids - cache.keys
        val updatable = cache.values.filter { it.isUpdatable(current) }.map { it.item.id }.toSet()
        val needed = notCached + updatable
        if (needed.isEmpty()) {
            return Result.success(cache.values.toList())
        }
        return remoteSource.fetchVideoList(needed).mapCatching { v ->
            val needsChannel =
                v.filter { cache[it.item.id]?.item?.channel?.iconUrl.isNullOrEmpty() }
            if (needsChannel.isEmpty()) {
                v
            } else {
                fetchChannelList(needsChannel.map { it.item.channel.id }.toSet()).map { c ->
                    val channels = c.associateBy { it.item.id }
                    needsChannel.map { n ->
                        val channel = channels.getValue(n.item.channel.id).item
                        n.map {
                            object : YouTubeVideo by it {
                                override val channel: YouTubeChannel get() = channel
                            } as YouTubeVideo
                        }
                    }
                }.map { it + (v - needsChannel.toSet()) }.getOrThrow()
            }
        }.map { v ->
            v.map { it.extend(old = cache[it.item.id]) } + (ids - needed).mapNotNull { cache[it] }
        }
    }
}
