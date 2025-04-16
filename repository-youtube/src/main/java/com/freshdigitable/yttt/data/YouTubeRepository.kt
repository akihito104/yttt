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
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.YouTubeLiveDataSource
import com.freshdigitable.yttt.logE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.stateIn
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
) : YouTubeDataSource, YouTubeLiveDataSource {
    internal var subscriptionFetchedAt: Instant? = null

    override suspend fun fetchAllSubscribe(pageSize: Long): Result<List<YouTubeSubscription>> {
        val cache = localSource.fetchAllSubscribe()
        if (cache.isFailure) {
            return cache
        }
        val res = remoteSource.fetchAllSubscribePaged(pageSize).reduce { _, v ->
            v.onSuccess { localSource.addSubscribes(it) }
        }.onSuccess { r ->
            subscriptionFetchedAt = dateTimeProvider.now()
            val c = cache.getOrNull() ?: emptyList()
            val deleted = c.map { it.id }.toSet() - r.map { it.id }.toSet()
            localSource.removeSubscribes(deleted)
        }
        return res
    }

    suspend fun fetchPagedSubscriptionSummary(): Flow<Result<List<YouTubeSubscriptionSummary>>> {
        val cache = localSource.fetchAllSubscribe()
        if (cache.isFailure) {
            logE(throwable = cache.exceptionOrNull()) { "fetchPagedSubscriptionSummary" }
            return flowOf(Result.failure(cache.exceptionOrNull()!!))
        }
        return flow {
            val res = mutableListOf<YouTubeSubscriptionSummary>()
            remoteSource.fetchAllSubscribePaged()
                .fold(Result.success(emptyList<YouTubeSubscription>())) { acc, value ->
                    value.onSuccess { v ->
                        val page = v - checkNotNull(acc.getOrNull()).toSet()
                        localSource.addSubscribes(page)
                        val summary = localSource.findSubscriptionSummaries(page.map { it.id })
                        emit(Result.success(res + summary))
                        res.addAll(summary)
                    }.onFailure {
                        emit(Result.failure(it))
                    }
                }.onSuccess { s ->
                    subscriptionFetchedAt = dateTimeProvider.now()
                    val c = cache.getOrNull() ?: emptyList()
                    val deleted = c.map { it.id } - s.map { it.id }.toSet()
                    localSource.removeSubscribes(deleted.toSet())
                }
        }
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<YouTubeChannelLog> {
        val pa = if (publishedAfter != null) {
            publishedAfter
        } else {
            val cache = localSource.fetchLiveChannelLogs(channelId, maxResult = 1).firstOrNull()
            cache?.dateTime?.plusSeconds(1) ?: dateTimeProvider.now().minus(activityMaxPeriod)
        }
        val res = remoteSource.fetchLiveChannelLogs(channelId, pa, maxResult)
        localSource.addLiveChannelLogs(res)
        return res
    }


    fun removeImageByUrl(url: Collection<String>) {
        localSource.removeImageByUrl(url)
    }

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<YouTubePlaylist> {
        val cache = localSource.fetchPlaylist(ids)
        val needed = ids - cache.map { it.id }.toSet()
        if (needed.isEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchPlaylist(needed)
        localSource.addPlaylist(remote)
        return cache + remote
    }

    suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long = 10,
    ): List<YouTubePlaylistItem> {
        val cache = localSource.fetchPlaylistWithItems(id)
        if (cache?.isUpdatable(dateTimeProvider.now()) == false) {
            return cache.items.toList()
        }
        val updatable = fetchPlaylistWithItemsUpdatable(id, maxResult, cache).onSuccess { u ->
            val uploadedAtAnotherChannel = u.items
                .filter { it.channel.id != it.videoOwnerChannelId }
                .mapNotNull { it.videoOwnerChannelId }
            if (uploadedAtAnotherChannel.isNotEmpty()) {
                fetchChannelList(uploadedAtAnotherChannel.toSet())
            }
            localSource.updatePlaylistWithItems(u)
        }
        return updatable.getOrNull()?.items?.toList() ?: emptyList()
    }

    suspend fun fetchPlaylistWithItems(
        summary: YouTubeSubscriptionSummary,
        current: Instant = dateTimeProvider.now(),
        maxResult: Long = 10,
    ): Result<YouTubePlaylistWithItems?> {
        val playlistId = checkNotNull(summary.uploadedPlaylistId)
        if (!summary.needsUpdatePlaylist(current)) {
            return Result.success(null)
        }
        val updatable = fetchPlaylistWithItemsUpdatable(
            playlistId,
            maxResult,
            localSource.fetchPlaylistWithItemSummaries(playlistId),
        ).onSuccess {
            localSource.updatePlaylistWithItems(it)
        }
        return updatable
    }

    private suspend fun fetchPlaylistWithItemsUpdatable(
        playlistId: YouTubePlaylist.Id,
        maxResult: Long = 10,
        cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>?
    ): Result<YouTubePlaylistWithItems> = kotlin.runCatching {
        val newItems = remoteSource.fetchPlaylistItems(playlistId, maxResult = maxResult)
        val fetchedAt = dateTimeProvider.now()
        cache?.update(newItems, fetchedAt)
            ?: YouTubePlaylistWithItems.newPlaylist(
                playlist = remoteSource.fetchPlaylist(setOf(playlistId)).first(),
                items = newItems,
                fetchedAt = fetchedAt,
            )
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
        val remoteRes = remoteSource.fetchChannelList(needed)
            .onSuccess { localSource.addChannelList(it) }
        if (remoteRes.isFailure) {
            return remoteRes
        }
        return remoteRes.map { it + cache }
    }

    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        val cache = localSource.fetchChannelSection(id)
        if (cache.isNotEmpty()) { // TODO: updatable
            return cache
        }
        val remote = remoteSource.fetchChannelSection(id)
        localSource.addChannelSection(remote)
        return remote
    }

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }

    override val videos: StateFlow<List<YouTubeVideoExtended>> = localSource.videos
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideoExtended>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        val videoCache = localSource.fetchVideoList(ids)
        if (videoCache.isFailure) {
            return Result.failure(videoCache.exceptionOrNull()!!)
        }
        val cache = checkNotNull(videoCache.getOrNull()).associateBy { it.id }
        val current = dateTimeProvider.now()
        val notCached = ids - cache.keys
        val updatable = cache.values.filter { it.isUpdatable(current) }.map { it.id }.toSet()
        val needed = notCached + updatable
        if (needed.isEmpty()) {
            return Result.success(cache.values.toList())
        }
        val videoRes = remoteSource.fetchVideoList(needed)
        return videoRes.map { v ->
            v.map { it.extend(old = cache[it.id], fetchedAt = dateTimeProvider.now()) } +
                (ids - needed).mapNotNull { cache[it] }
        }
    }

    override suspend fun addVideo(video: Collection<YouTubeVideoExtended>) {
        localSource.addVideo(video)
    }

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>) {
        if (ids.isEmpty()) {
            return
        }
        localSource.removeVideo(ids)
    }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        localSource.addFreeChatItems(ids)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        localSource.removeFreeChatItems(ids)
    }

    override suspend fun cleanUp() {
        localSource.cleanUp()
    }

    override suspend fun deleteAllTables() {
        localSource.deleteAllTables()
    }
}
