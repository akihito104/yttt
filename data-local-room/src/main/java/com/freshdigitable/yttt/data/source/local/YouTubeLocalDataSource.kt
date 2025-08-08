package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistUpdatableDb
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionEtagTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    imageDataSource: ImageDataSource,
    private val ioScope: IoScope,
) : YouTubeDataSource.Local, ImageDataSource by imageDataSource {
    override val videos: Flow<List<YouTubeVideoExtended>> = dao.watchAllUnfinishedVideos()

    override suspend fun findSubscriptionSummaries(
        ids: Collection<YouTubeSubscription.Id>,
    ): List<YouTubeSubscriptionSummary> = ioScope.asResult {
        dao.findSubscriptionSummaries(ids)
    }.getOrThrow()

    override suspend fun findSubscriptionSummariesByOffset(
        offset: Int,
        pageSize: Int,
    ): List<YouTubeSubscriptionSummary> = ioScope.asResult {
        dao.findSubscriptionSummariesByOffset(offset, pageSize)
    }.getOrThrow()

    override var subscriptionsFetchedAt: Instant = Instant.EPOCH
    override var subscriptionsRelevanceOrderedFetchedAt: Instant = Instant.EPOCH

    override suspend fun addPagedSubscription(subscription: Collection<YouTubeSubscription>) {
        dao.addSubscriptions(subscription)
    }

    override suspend fun findSubscriptionQuery(offset: Int): YouTubeSubscriptionQuery? =
        dao.findSubscriptionQuery(offset)

    override suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id> =
        dao.fetchAllSubscriptionIds().toSet()

    override suspend fun addSubscriptionEtag(offset: Int, nextPageToken: String?, eTag: String) {
        dao.addSubscriptionEtag(YouTubeSubscriptionEtagTable(offset, nextPageToken, eTag))
    }

    override suspend fun removeSubscribesByRemainingIds(subscriptions: Set<YouTubeSubscription.Id>) {
        dao.removeSubscriptionsByRemainingIds(subscriptions)
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): Result<List<YouTubeChannelLog>> = runCatching {
        if (publishedAfter != null) {
            dao.findChannelLogs(channelId, publishedAfter, maxResult)
        }
        dao.findChannelLogs(channelId, maxResult)
    }

    override suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>) {
        dao.addChannelLogs(channelLogs)
    }

    override suspend fun fetchUpdatableVideoIds(current: Instant): List<YouTubeVideo.Id> =
        dao.fetchUpdatableVideoIds(current)

    private val playlist = mutableMapOf<YouTubePlaylist.Id, Updatable<YouTubePlaylist>>()
    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> =
        Result.success(ids.mapNotNull { playlist[it] })

    override suspend fun addPlaylist(playlist: Collection<Updatable<YouTubePlaylist>>) {
        this.playlist.putAll(playlist.associateBy { it.item.id })
    }

    override suspend fun addChannelList(channel: Collection<YouTubeChannel>) {
        dao.addChannels(channel.map { YouTubeChannelTable(it.id, it.title, it.iconUrl) })
    }

    override suspend fun addChannelRelatedPlaylists(channel: List<YouTubeChannelRelatedPlaylist>) {
        dao.addChannelRelatedPlaylistList(channel)
    }

    override suspend fun fetchPlaylistWithItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>?> = ioScope.asResult {
        database.withTransaction {
            val playlist = dao.findUpdatablePlaylistById(id) ?: return@withTransaction null
            val items = dao.findPlaylistItemByPlaylistId(id)
            YouTubePlaylistWithItem.fromCache(playlist, items)
        }
    }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItems>?> = ioScope.asResult {
        dao.findPlaylistWithItemIds(id)?.toUpdatable()
    }

    override suspend fun updatePlaylistWithItems(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) {
        dao.updatePlaylistWithItems(item, cacheControl)
    }

    override suspend fun updatePlaylistWithItemsCacheControl(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) {
        dao.updatePlaylistWithItemsCacheControl(item, cacheControl)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>> =
        ioScope.asResult {
            fetchByIds(ids) { findVideosById(it) }.flatten()
        }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, true, YouTubeVideo.MAX_AGE_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, false, YouTubeVideo.MAX_AGE_DEFAULT)
    }

    override suspend fun addVideo(video: Collection<Updatable<YouTubeVideoExtended>>) =
        ioScope.asResult { dao.addVideos(video) }.getOrThrow()

    override suspend fun cleanUp() {
        database.youTubeChannelLogDao.deleteTable()
        removeNotExistVideos()
    }

    private suspend fun removeNotExistVideos() {
        database.withTransaction {
            val removingId = dao.findUnusedVideoIds().toSet()
            removeVideo(removingId)
            dao.removeVideoIsArchivedEntities(removingId)
            database.youTubeVideoIsArchivedDao.removeUnusedEntities()
        }
        database.withTransaction {
            val archivedIds = dao.findAllArchivedVideos().toSet()
            removeVideo(archivedIds)
        }
    }

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>): Unit = ioScope.asResult {
        val thumbs = dao.findThumbnailUrlByIds(ids)
        fetchByIds(ids) { i ->
            val items = i.map { YouTubeVideoIsArchivedTable(it, true) }
            database.withTransaction {
                addVideoIsArchivedEntities(items)
                removeVideos(i)
            }
        }
        removeImageByUrl(thumbs)
    }.getOrThrow()

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannel>> =
        ioScope.asResult { dao.findChannels(ids) }

    override suspend fun fetchChannelRelatedPlaylistList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannelRelatedPlaylist>> =
        ioScope.asResult { dao.findChannelRelatedPlaylists(ids) }

    override suspend fun fetchChannelDetailList(ids: Set<YouTubeChannel.Id>): Result<List<Updatable<YouTubeChannelDetail>>> =
        ioScope.asResult { dao.findChannelDetail(ids) }

    override suspend fun addChannelDetailList(channelDetail: Collection<Updatable<YouTubeChannelDetail>>) {
        dao.addChannelDetails(channelDetail)
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> {
        return Result.success(channelSections[id] ?: emptyList())
    }

    override suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>) {
        if (channelSection.isEmpty()) {
            return
        }
        channelSections[channelSection.first().channelId] = channelSection.toList()
    }

    override suspend fun deleteAllTables() {
        dao.deleteTable()
    }

    private suspend fun <I : YouTubeId, O> fetchByIds(
        ids: Set<I>,
        query: suspend YouTubeDao.(Set<I>) -> O,
    ): List<O> {
        return if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size < YouTubeDataSource.MAX_BATCH_SIZE) {
            listOf(dao.query(ids))
        } else {
            val a = database.withTransaction {
                ids.chunked(YouTubeDataSource.MAX_BATCH_SIZE).map { dao.query(it.toSet()) }
            }
            listOf(a).flatten()
        }
    }
}

internal fun YouTubePlaylistWithItem.Companion.fromCache(
    playlist: YouTubePlaylistUpdatableDb,
    items: List<YouTubePlaylistItemDetail>,
): Updatable<YouTubePlaylistWithItemDetails> =
    PlaylistAndItemsLocal(playlist, items).toUpdatable(playlist.cacheControl)

private class PlaylistAndItemsLocal(
    private val _playlist: YouTubePlaylistUpdatableDb,
    override val items: List<YouTubePlaylistItemDetail>,
) : YouTubePlaylistWithItemDetails {
    override val playlist: YouTubePlaylist get() = _playlist.item
    override val eTag: String? get() = _playlist.eTag
}
