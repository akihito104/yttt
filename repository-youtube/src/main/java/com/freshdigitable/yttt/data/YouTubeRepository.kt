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
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail.Companion.isFromAnotherChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.recoverFromNotModified
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val remoteSource: YouTubeDataSource.Remote,
    private val localSource: YouTubeDataSource.Local,
    private val extendedSource: YouTubeDataSource.Extended,
    private val dateTimeProvider: DateTimeProvider,
) : YouTubeDataSource, YouTubeDataSource.Extended by extendedSource, ImageDataSource by localSource {
    override suspend fun fetchPagedSubscription(
        query: YouTubeSubscriptionQuery,
    ): Result<NetworkResponse<List<YouTubeSubscription>>> =
        remoteSource.fetchPagedSubscription(query).onSuccess {
            addPagedSubscription(it.item)
            if (it.nextPageToken == null) {
                val fetchedAt = it.cacheControl.fetchedAt!!
                when (query.order) {
                    YouTubeSubscriptionQuery.Order.ALPHABETICAL ->
                        subscriptionsFetchedAt = fetchedAt

                    YouTubeSubscriptionQuery.Order.RELEVANCE ->
                        subscriptionsRelevanceOrderedFetchedAt = fetchedAt
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
            localSource.addChannelLogs(it)
        }
    }

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> =
        localSource.fetchPlaylist(ids).mapCatching { cache ->
            val needed = ids - cache.map { it.item.id }.toSet()
            if (needed.isEmpty()) {
                cache
            } else {
                remoteSource.fetchPlaylist(needed)
                    .onSuccess { localSource.addPlaylist(it) }
                    .getOrThrow() + cache
            }
        }

    override suspend fun fetchPlaylistWithItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>> =
        localSource.fetchPlaylistWithItemDetails(id, maxResult).mapCatching { cache ->
            if (cache?.isFresh(dateTimeProvider.now()) == true) {
                cache
            } else {
                remoteSource.fetchPlaylistWithItemDetails(id, maxResult, cache?.item)
                    .onSuccess { u ->
                        val uploadedAtAnotherChannel = u.item.items
                            .filter { it.isFromAnotherChannel }
                            .mapNotNull { it.videoOwnerChannelId }
                        if (uploadedAtAnotherChannel.isNotEmpty()) {
                            fetchChannelList(uploadedAtAnotherChannel.toSet())
                        }
                        updatePlaylistWithItems(u.item, u.cacheControl)
                    }.getOrThrow()
            }
        }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItems>?> {
        val c = localSource.fetchPlaylistWithItems(id, maxResult).getOrNull()?.item
        return remoteSource.fetchPlaylistWithItems(id, maxResult, c)
            .onSuccess { updatePlaylistWithItems(it.item, it.cacheControl) }
            .recoverFromNotModified { cacheControl ->
                checkNotNull(c).toUpdatable(cacheControl).also {
                    updatePlaylistWithItemsCacheControl(it.item, it.cacheControl)
                }
            }
            .map { it }
    }

    override suspend fun fetchChannelList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<YouTubeChannel>> = localSource.fetchChannelList(ids).mapCatching { cache ->
        val needed = ids - cache.map { it.id }
        if (needed.isEmpty()) {
            cache
        } else {
            remoteSource.fetchChannelList(needed)
                .onSuccess { localSource.addChannelList(it) }
                .getOrThrow() + cache
        }
    }

    override suspend fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<YouTubeChannelRelatedPlaylist>> =
        localSource.fetchChannelRelatedPlaylistList(ids).mapCatching { cache ->
            val needed = ids - cache.map { it.id }
            if (needed.isEmpty()) {
                cache
            } else {
                remoteSource.fetchChannelRelatedPlaylistList(needed)
                    .onSuccess { localSource.addChannelRelatedPlaylists(it) }
                    .getOrThrow() + cache
            }
        }

    override suspend fun fetchChannelDetailList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<Updatable<YouTubeChannelDetail>>> = localSource.fetchChannelDetailList(ids).mapCatching { cache ->
        val current = dateTimeProvider.now()
        val freshItems = cache.filter { it.isFresh(current) }
        val needed = ids - freshItems.map { it.item.id }.toSet()
        if (needed.isEmpty()) {
            cache
        } else {
            remoteSource.fetchChannelDetailList(needed)
                .map { c -> c.map { it.overrideMaxAge(YouTubeChannelDetail.MAX_AGE) } }
                .onSuccess { localSource.addChannelDetailList(it) }
                .getOrThrow() + freshItems
        }
    }

    override suspend fun fetchChannelSection(
        id: YouTubeChannel.Id,
    ): Result<List<YouTubeChannelSection>> = localSource.fetchChannelSection(id).mapCatching { cache ->
        cache.ifEmpty {
            remoteSource.fetchChannelSection(id)
                .onSuccess { localSource.addChannelSection(it) }
                .getOrThrow()
        }
    }

    companion object {
        private val ACTIVITY_MAX_PERIOD = Period.ofDays(7)
        private fun Updatable<YouTubeVideoExtended>?.hasIconUrl(): Boolean =
            this?.item?.channel?.iconUrl?.isNotEmpty() == true
    }

    override suspend fun fetchVideoList(
        ids: Set<YouTubeVideo.Id>,
    ): Result<List<Updatable<YouTubeVideoExtended>>> = extendedSource.fetchVideoList(ids).mapCatching { video ->
        val cache = video.associateBy { it.item.id }
        val notCached = ids - cache.keys
        val current = dateTimeProvider.now()
        val updatable = video.filter { it.isUpdatable(current) }.map { it.item.id }.toSet()
        val needed = notCached + updatable
        if (needed.isEmpty()) {
            video
        } else {
            val remoteVideo = remoteSource.fetchVideoList(needed).getOrThrow()
            val updatableVideos = remoteVideo.filter { !cache[it.item.id].hasIconUrl() }
            if (updatableVideos.isEmpty()) {
                remoteVideo
            } else {
                val channelIds = updatableVideos.map { it.item.channel.id }.toSet()
                val channels = fetchChannelList(channelIds)
                    .onSuccess { localSource.addChannelList(it) }
                    .getOrThrow()
                    .associateBy { it.id }
                val updated = updatableVideos.map { u ->
                    val channel = channels.getValue(u.item.channel.id)
                    u.map { YouTubeVideoImpl(it, channel) as YouTubeVideo }
                }
                updated + (remoteVideo - updatableVideos.toSet())
            }.map { it.extend(old = cache[it.item.id]) } + (ids - needed).mapNotNull { cache[it] }
        }
    }
}

private class YouTubeVideoImpl(
    video: YouTubeVideo,
    override val channel: YouTubeChannel,
) : YouTubeVideo by video
