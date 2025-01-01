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
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
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
    override val videos: Flow<List<YouTubeVideo>> = dao.watchAllUnfinishedVideos()
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
        dao.addChannelLogs(channelLogs, current = dateTimeProvider.now())
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
        if (items == null) {
            dao.setPlaylistItems(
                id = id,
                items = emptyList(),
                lastModified = dateTimeProvider.now(),
                maxAge = YouTubePlaylistTable.getMaxAgeUpperLimit(false),
            )
            return
        }
        if (items.isEmpty()) {
            dao.setPlaylistItems(
                id = id,
                items = emptyList(),
                lastModified = dateTimeProvider.now(),
            )
            return
        }
        check(items.all { it.playlistId == id })
        val cache = dao.findPlaylistById(id)
        if (cache == null) {
            dao.setPlaylistItems(id = id, items = items, lastModified = dateTimeProvider.now())
            return
        }
        val playlistItems = dao.findPlaylistItemByPlaylistId(cache.id)
        val cachedIds = playlistItems.map { it.id }.toSet()
        val newIds = items.map { it.id }.toSet()
        val isNotModified = (cachedIds - newIds).isEmpty() && (newIds - cachedIds).isEmpty()
        val maxAge = if (isNotModified) {
            val boarder = dateTimeProvider.now().minus(YouTubePlaylistTable.RECENTLY_BOARDER)
            val isPublishedRecently = items.any { boarder < it.publishedAt }
            val maxAgeMax = YouTubePlaylistTable.getMaxAgeUpperLimit(isPublishedRecently)
            cache.maxAge.multipliedBy(2).coerceIn(YouTubePlaylistTable.MAX_AGE_DEFAULT..maxAgeMax)
        } else {
            YouTubePlaylistTable.MAX_AGE_DEFAULT
        }
        dao.setPlaylistItems(
            id = id,
            maxAge = maxAge,
            items = items,
            lastModified = dateTimeProvider.now(),
        )
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideo> {
        val current = dateTimeProvider.now()
        return fetchByIds(ids) { findVideosById(it, current = current) }.flatten()
    }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, true, dateTimeProvider.now() + EXPIRATION_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, false, dateTimeProvider.now() + EXPIRATION_DEFAULT)
    }

    // TODO: omit findFreeChatItems()
    // In the current implementation, this function is implicitly assumed to receive a `video` items from a remote source,
    // so the value of `isFreeChat` is obtained from a local source. Since it would be more natural and easier to understand
    // if the value of `isFreeChat` could be obtained directly from the `video` items, we plan to omit the access to the local
    // source here.
    override suspend fun addVideo(video: Collection<YouTubeVideo>) = withContext(ioDispatcher) {
        val upcoming =
            video.filter { it.liveBroadcastContent == YouTubeVideo.BroadcastType.UPCOMING }
                .map { it.id }
        val v = dao.findFreeChatItems(upcoming).associateBy { it.videoId }

        val isArchived = video.map { YouTubeVideoIsArchivedTable(it.id, it.isArchived) }
        dao.addVideoIsArchivedEntities(isArchived)

        val current = dateTimeProvider.now()
        val defaultExpiredAt = current + EXPIRATION_DEFAULT
        val expiring = video.associateWith {
            when {
                it.isFreeChat == true || v[it.id]?.isFreeChat == true -> current + EXPIRATION_FREE_CHAT
                it.isUpcoming() ->
                    defaultExpiredAt.coerceAtMost(it.scheduledStartDateTime ?: defaultExpiredAt)

                it.isNowOnAir() -> current + EXPIRATION_ON_AIR
                it.isArchived -> EXPIRATION_MAX
                else -> defaultExpiredAt
            }
        }
        dao.addVideos(expiring)
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
            fetchByIds(archivedIds) { ids ->
                val items = ids.map { YouTubeVideoIsArchivedTable(it, true) }
                addVideoIsArchivedEntities(items)
            }
            removeVideo(archivedIds)
        }
    }

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>): Unit = withContext(ioDispatcher) {
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

    companion object {
        /**
         * cache expiration duration for archived video (`Long.MAX_VALUE`, because of DB limitation :( )
         */
        private val EXPIRATION_MAX: Instant = Instant.ofEpochMilli(Long.MAX_VALUE)

        /**
         * cache expiration duration for default (10 min.)
         */
        private val EXPIRATION_DEFAULT = Duration.ofMinutes(10)

        /**
         * cache expiration duration for free chat (1 day)
         */
        private val EXPIRATION_FREE_CHAT = Duration.ofDays(1)

        /**
         * cache expiration duration for on air stream (1 min.)
         */
        private val EXPIRATION_ON_AIR = Duration.ofMinutes(1)
    }
}
