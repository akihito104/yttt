package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemsUpdatable
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    private val ioDispatcher: CoroutineDispatcher,
) : YoutubeDataSource.Local, ImageDataSource by imageDataSource {
    override val videos: Flow<List<YouTubeVideoExtended>> = dao.watchAllUnfinishedVideos()
    override suspend fun findSubscriptionSummaries(
        ids: Collection<YouTubeSubscription.Id>,
    ): List<YouTubeSubscriptionSummary> = withContext(ioDispatcher) {
        dao.findSubscriptionSummaries(ids)
    }

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> =
        withContext(ioDispatcher) {
            dao.findAllSubscriptions()
        }

    override suspend fun addSubscribes(subscriptions: Collection<YouTubeSubscription>) =
        withContext(ioDispatcher) {
            dao.addSubscriptions(subscriptions)
        }

    override suspend fun removeSubscribes(subscriptions: Set<YouTubeSubscription.Id>) {
        dao.removeSubscriptions(subscriptions)
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<YouTubeChannelLog> {
        if (publishedAfter != null) {
            return dao.findChannelLogs(channelId, publishedAfter, maxResult)
        }
        return dao.findChannelLogs(channelId, maxResult)
    }

    override suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>) {
        dao.addChannelLogs(channelLogs)
    }

    /**
     * returns playlist item list. `null` means that list items have not cached yet or have expired (needs download from remote).
     * returned empty list means that there is no items in the playlist (because of not updated yet or the playlist is to be private).
     */
    override suspend fun fetchPlaylistItems(id: YouTubePlaylist.Id): List<YouTubePlaylistItem>? {
        return database.withTransaction {
            val p = dao.findPlaylistById(id, since = dateTimeProvider.now())
                ?: return@withTransaction null
            dao.findPlaylistItemByPlaylistId(p.id)
        }
    }

    override suspend fun fetchPlaylistItemSummary(
        playlistId: YouTubePlaylist.Id,
        maxResult: Long,
    ): List<YouTubePlaylistItemSummary> = dao.findPlaylistItemSummary(playlistId, maxResult)

    override suspend fun setPlaylistItemsByPlaylistId(
        id: YouTubePlaylist.Id,
        items: Collection<YouTubePlaylistItem>?,
    ) {
        val entity = if (items.isNullOrEmpty()) {
            YouTubePlaylistItemsUpdatable
                .nullOrEmpty(playlistId = id, items = items, fetchedAt = dateTimeProvider.now())
        } else {
            YouTubePlaylistItemsUpdatable(
                playlistId = id,
                cachedPlaylistWithItems = fetchPlaylistWithItems(id),
                newItems = items,
                fetchedAt = dateTimeProvider.now(),
            )
        }
        replacePlaylistItemsWithUpdatable(entity)
    }

    override suspend fun fetchPlaylistWithItems(id: YouTubePlaylist.Id): YouTubePlaylistWithItems? =
        database.withTransaction {
            val playlist = dao.findPlaylistById(id) ?: return@withTransaction null
            val items = dao.findPlaylistItemByPlaylistId(playlist.id)
            object : YouTubePlaylistWithItems, Updatable by playlist {
                override val playlist: YouTubePlaylist = playlist
                override val items: List<YouTubePlaylistItem> = items
            }
        }

    override suspend fun replacePlaylistItemsWithUpdatable(updatable: YouTubePlaylistItemsUpdatable) {
        dao.setPlaylistItems(updatable)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideoExtended> {
        return fetchByIds(ids) { findVideosById(it) }.flatten()
    }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        val updatableAt =
            dateTimeProvider.now() + YouTubeVideoUpdatable.UPDATABLE_DURATION_FREE_CHAT
        dao.addFreeChatItems(ids, true, updatableAt)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        val updatableAt = dateTimeProvider.now() + YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT
        dao.addFreeChatItems(ids, false, updatableAt)
    }

    override suspend fun addVideo(
        video: Collection<YouTubeVideoExtended>,
    ) = withContext(ioDispatcher) {
        dao.addVideos(video)
    }

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

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>): Unit = withContext(ioDispatcher) {
        fetchByIds(ids) { i ->
            val items = i.map { YouTubeVideoIsArchivedTable(it, true) }
            addVideoIsArchivedEntities(items)
        }
        val thumbs = dao.findThumbnailUrlByIds(ids)
        fetchByIds(ids) { removeVideos(it) }
        removeImageByUrl(thumbs)
    }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
        return dao.findChannelDetail(ids, current = dateTimeProvider.now())
    }

    override suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>) {
        dao.addChannelDetails(channelDetail, dateTimeProvider.now() + Duration.ofDays(1))
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    override suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>) {
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
        } else if (ids.size < 50) {
            listOf(dao.query(ids))
        } else {
            val a = database.withTransaction {
                ids.chunked(50).map { dao.query(it.toSet()) }
            }
            listOf(a).flatten()
        }
    }
}
