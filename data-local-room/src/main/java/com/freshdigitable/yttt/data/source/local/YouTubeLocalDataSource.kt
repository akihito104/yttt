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
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dateTimeProvider: DateTimeProvider,
) : YoutubeDataSource.Local {
    private val dao: YouTubeDao get() = database.youtubeDao
    override val videos: Flow<List<YouTubeVideo>> = dao.watchAllUnfinishedVideos()

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> =
        withContext(Dispatchers.IO) {
            dao.findAllSubscriptions()
        }

    override suspend fun fetchAllSubscriptionSummary(): List<YouTubeSubscriptionSummary> =
        dao.findAllSubscriptionSummary()

    override suspend fun addSubscribes(subscriptions: Collection<YouTubeSubscription>) =
        withContext(Dispatchers.IO) {
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
        items: Collection<YouTubePlaylistItem>,
    ) {
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
            val isPublishedRecently = items.any { boarder.isAfter(it.publishedAt) }
            val maxAgeMax = YouTubePlaylistTable.getMaxAgeUpperLimit(isPublishedRecently)
            cache.maxAge.multipliedBy(2).coerceAtMost(maxAgeMax)
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
        return fetchByIds(ids) { findVideosById(it, current = dateTimeProvider.now()) }.flatten()
    }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, true, dateTimeProvider.now() + EXPIRATION_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, false, dateTimeProvider.now() + EXPIRATION_DEFAULT)
    }

    override suspend fun addVideo(video: Collection<YouTubeVideo>) = withContext(Dispatchers.IO) {
        val current = dateTimeProvider.now()
        val defaultExpiredAt = current + EXPIRATION_DEFAULT
        dao.addVideoIsArchivedEntities(video.map {
            YouTubeVideoIsArchivedTable(it.id, it.isArchived)
        })
        val archived = video.filter { it.isArchived }.toSet()
        val unfinished = video - archived
        val v = dao.findFreeChatItems(unfinished.map { it.id }).associateBy { it.videoId }
        val expiring = unfinished.associateWith {
            when {
                it.isFreeChat == true || v[it.id]?.isFreeChat == true -> current + EXPIRATION_FREE_CHAT
                it.isUpcoming() -> defaultExpiredAt.coerceAtMost(checkNotNull(it.scheduledStartDateTime))
                it.isNowOnAir() -> current + EXPIRATION_ON_AIR
                it.isArchived -> EXPIRATION_MAX // for fail safe
                else -> defaultExpiredAt
            }
        }
        dao.addVideos(expiring)
    }

    override suspend fun cleanUp() {
        dao.removeAllChannelLogs()
        removeNotExistVideos()
    }

    private suspend fun removeNotExistVideos() {
        val removingId = dao.findUnusedVideoIds().toSet()
        removeVideo(removingId)
        dao.removeVideoIsArchivedEntities(removingId)
        database.withTransaction {
            val archivedIds = dao.findAllArchivedVideos().toSet()
            fetchByIds(archivedIds) { ids ->
                addVideoIsArchivedEntities(ids.map {
                    YouTubeVideoIsArchivedTable(it, true)
                })
            }
            removeVideo(archivedIds)
        }
    }

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>): Unit =
        withContext(Dispatchers.IO) {
            fetchByIds(ids) { removeVideos(it) }
        }

    override suspend fun findAllUnfinishedVideos(): List<YouTubeVideo> {
        return dao.findAllUnfinishedVideoList()
    }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
        return dao.findChannelDetail(ids)
    }

    override suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>) {
        dao.addChannelDetails(channelDetail)
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    override suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>) {
        channelSections[channelSection.first().channelId] = channelSection.toList()
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
        private val YouTubeVideo.isArchived: Boolean
            get() = !isLiveStream() || actualEndDateTime != null
    }
}
