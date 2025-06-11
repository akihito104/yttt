package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemIds
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemSummaries
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    imageDataSource: ImageDataSource,
    private val dateTimeProvider: DateTimeProvider,
    private val ioScope: IoScope,
) : YouTubeDataSource.Local, ImageDataSource by imageDataSource {
    override val videos: Flow<List<YouTubeVideoExtended>> = dao.watchAllUnfinishedVideos()
    override suspend fun findSubscriptionSummaries(
        ids: Collection<YouTubeSubscription.Id>,
    ): List<YouTubeSubscriptionSummary> = ioScope.asResult {
        dao.findSubscriptionSummaries(ids)
    }.getOrNull()!!  // FIXME

    override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions>> =
        ioScope.asResultFlow {
            val items = dao.findAllSubscriptions()
            val subs = YouTubeSubscriptions.create(items, _subscriptionsFetchedAt)
            emit(Result.success(subs))
        }

    override suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id> =
        dao.fetchAllSubscriptionIds().toSet()

    private var _subscriptionsFetchedAt: Instant = Instant.EPOCH
    override val subscriptionsFetchedAt: Instant get() = _subscriptionsFetchedAt

    override suspend fun addSubscribes(subscriptions: YouTubeSubscriptions) =
        ioScope.asResult {
            dao.addSubscriptions(subscriptions.items)
            if (subscriptions is YouTubeSubscriptions.Updated) {
                _subscriptionsFetchedAt = subscriptions.lastUpdatedAt
            }
        }.getOrNull()!! // FIXME

    override suspend fun removeSubscribes(subscriptions: Set<YouTubeSubscription.Id>) {
        dao.removeSubscriptions(subscriptions)
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

    override suspend fun fetchPlaylistItemSummary(
        playlistId: YouTubePlaylist.Id,
        maxResult: Long,
    ): List<YouTubePlaylistItemSummary> = dao.findPlaylistItemSummary(playlistId, maxResult)

    private val playlist = mutableMapOf<YouTubePlaylist.Id, YouTubePlaylist>()
    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<YouTubePlaylist>> =
        Result.success(ids.mapNotNull { playlist[it] })

    override suspend fun addPlaylist(playlist: Collection<YouTubePlaylist>) {
        this.playlist.putAll(playlist.associateBy { it.id })
    }

    override suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<List<YouTubePlaylistItem>> = ioScope.asResult { dao.findPlaylistItemByPlaylistId(id) }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>?,
    ): Result<YouTubePlaylistWithItems?> = ioScope.asResult {
        database.withTransaction {
            val playlist = dao.findPlaylistById(id) ?: return@withTransaction null
            val items = dao.findPlaylistItemByPlaylistId(playlist.id)
            YouTubePlaylistWithItems.create(playlist, items)
        }
    }

    override suspend fun fetchPlaylistWithItemSummaries(id: YouTubePlaylist.Id): YouTubePlaylistWithItemSummaries? =
        dao.findPlaylistWithItemSummaries(id)

    override suspend fun updatePlaylistWithItems(updatable: YouTubePlaylistWithItems) {
        dao.updatePlaylistWithItems(updatable)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideoExtended>> =
        ioScope.asResult {
            fetchByIds(ids) { findVideosById(it) }.flatten()
        }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, true, YouTubeVideoUpdatable.UPDATABLE_DURATION_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, false, YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT)
    }

    override suspend fun addVideo(
        video: Collection<YouTubeVideoExtended>,
    ) = ioScope.asResult { dao.addVideos(video) }.getOrThrow()

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
        fetchByIds(ids) { i ->
            val items = i.map { YouTubeVideoIsArchivedTable(it, true) }
            addVideoIsArchivedEntities(items)
        }
        val thumbs = dao.findThumbnailUrlByIds(ids)
        fetchByIds(ids) { removeVideos(it) }
        removeImageByUrl(thumbs)
    }.getOrNull()!! // FIXME

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannelDetail>> =
        ioScope.asResult {
            dao.findChannelDetail(ids, current = dateTimeProvider.now())
        }

    override suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>) {
        dao.addChannelDetails(channelDetail, dateTimeProvider.now() + Duration.ofDays(1))
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
