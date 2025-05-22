package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemIds
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.YouTubeLiveDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
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
    coroutineScope: CoroutineScope,
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

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<YouTubePlaylist>> {
        val cacheRes = localSource.fetchPlaylist(ids)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = checkNotNull(cacheRes.getOrNull())
        val needed = ids - cache.map { it.id }.toSet()
        if (needed.isEmpty()) {
            return cacheRes
        }
        return remoteSource.fetchPlaylist(needed).onSuccess {
            localSource.addPlaylist(it)
        }.map { it + cache }
    }

    override suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<List<YouTubePlaylistItem>> {
        val cacheRes = localSource.fetchPlaylistWithItems(id, maxResult)
        if (cacheRes.isFailure) {
            return Result.failure(checkNotNull(cacheRes.exceptionOrNull()))
        }
        val cache = cacheRes.getOrNull()
        if (cache?.isUpdatable(dateTimeProvider.now()) == false) {
            return Result.success(cache.items)
        }
        return fetchPlaylistWithItems(id, maxResult, cache).map { it?.items ?: emptyList() }
    }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>?,
    ): Result<YouTubePlaylistWithItems?> = fetchPlaylistWithItemsFromRemote(id, maxResult, cache)
        .onSuccess { u ->
            val uploadedAtAnotherChannel = u.items
                .filter { it.channel.id != it.videoOwnerChannelId }
                .mapNotNull { it.videoOwnerChannelId }
            if (uploadedAtAnotherChannel.isNotEmpty()) {
                fetchChannelList(uploadedAtAnotherChannel.toSet())
            }
            localSource.updatePlaylistWithItems(u)
        }

    private suspend fun fetchPlaylistWithItemsFromRemote(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>?,
    ): Result<YouTubePlaylistWithItems> {
        return if (cache != null) {
            remoteSource.fetchPlaylistItems(id, maxResult).map {
                cache.update(it, dateTimeProvider.now())
            }
        } else {
            val playlistRes = remoteSource.fetchPlaylist(setOf(id)).map { it.firstOrNull() }
            val playlist = if (playlistRes.isFailure) {
                return Result.failure(checkNotNull(playlistRes.exceptionOrNull()))
            } else {
                playlistRes.getOrNull()
                    ?: return Result.failure(IOException("playlist:${id.value} not found"))
            }
            remoteSource.fetchPlaylistItems(id, maxResult).map {
                YouTubePlaylistWithItems.newPlaylist(
                    playlist = playlist,
                    items = it,
                    fetchedAt = dateTimeProvider.now(),
                )
            }
        }
    }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannelDetail>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        val cacheRes = localSource.fetchChannelList(ids)
        if (cacheRes.isFailure) {
            return cacheRes
        }
        val cache = cacheRes.getOrNull() ?: emptyList()
        val needed = ids - cache.map { it.id }.toSet()
        if (needed.isEmpty()) {
            return cacheRes
        }
        return remoteSource.fetchChannelList(needed)
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

    override val videos: StateFlow<List<YouTubeVideoExtended>> = localSource.videos
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideoExtended>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        val cache = localSource.fetchVideoList(ids)
            .onFailure { return Result.failure(it) }
            .map { v -> v.associateBy { it.id } }
            .getOrDefault(emptyMap())
        val current = dateTimeProvider.now()
        val notCached = ids - cache.keys
        val updatable = cache.values.filter { it.isUpdatable(current) }.map { it.id }.toSet()
        val needed = notCached + updatable
        if (needed.isEmpty()) {
            return Result.success(cache.values.toList())
        }
        return remoteSource.fetchVideoList(needed).mapCatching { v ->
            val needsChannel = v.filter { cache[it.id]?.channel?.iconUrl.isNullOrEmpty() }
            if (needsChannel.isEmpty()) {
                v
            } else {
                fetchChannelList(needsChannel.map { it.channel.id }.toSet()).map { c ->
                    val channels = c.associateBy { it.id }
                    needsChannel.map {
                        val channel = channels.getValue(it.channel.id)
                        object : YouTubeVideo by it {
                            override val channel: YouTubeChannel get() = channel
                        }
                    }
                }.map { it + (v - needsChannel.toSet()) }.getOrThrow()
            }
        }.map { v ->
            val fetchedAt = dateTimeProvider.now()
            v.map { it.extend(old = cache[it.id], fetchedAt = fetchedAt) } +
                (ids - needed).mapNotNull { cache[it] }
        }
    }
}
