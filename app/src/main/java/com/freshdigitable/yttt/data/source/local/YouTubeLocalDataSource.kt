package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLocalDataSource @Inject constructor(
    private val database: AppDatabase,
) : YoutubeDataSource {
    private val dao: YouTubeDao get() = database.youtubeDao
    val subscriptions: Flow<List<YouTubeSubscription>> = dao.watchAllSubscriptions()
    val videos: Flow<List<YouTubeVideo>> = dao.watchAllUnfinishedVideos()

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> =
        withContext(Dispatchers.IO) {
            dao.findAllSubscriptions()
        }

    suspend fun addSubscribes(subscriptions: Collection<YouTubeSubscription>) =
        withContext(Dispatchers.IO) {
            dao.addSubscriptions(subscriptions)
        }

    suspend fun removeSubscribes(subscriptions: Collection<YouTubeSubscription.Id>) {
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

    suspend fun addLiveChannelLogs(channelLogs: List<YouTubeChannelLog>) {
        dao.addChannelLogs(channelLogs)
    }

    suspend fun fetchPlaylistItems(id: YouTubePlaylist.Id): List<YouTubePlaylistItem> {
        return dao.findPlaylistById(id, since = Instant.now())?.playlistItems
            ?: emptyList()
    }

    suspend fun setPlaylistItemsByPlaylistId(
        id: YouTubePlaylist.Id,
        items: Collection<YouTubePlaylistItem>,
    ) {
        if (items.isEmpty()) {
            dao.setPlaylistItems(id = id, items = emptyList())
            return
        }
        check(items.all { it.playlistId == id })
        val cache = dao.findPlaylistById(id)
        if (cache == null) {
            dao.setPlaylistItems(id = id, items = items)
            return
        }
        val cachedIds = cache.playlistItems.map { it.id }.toSet()
        val newIds = items.map { it.id }.toSet()
        val isNotModified = (cachedIds - newIds).isEmpty() && (newIds - cachedIds).isEmpty()
        val maxAge = if (isNotModified) {
            val boarder = Instant.now().minus(YouTubePlaylistTable.RECENTLY_BOARDER)
            val isPublishedRecently = items.any { boarder.isAfter(it.publishedAt) }
            val maxAgeMax = YouTubePlaylistTable.getMaxAgeUpperLimit(isPublishedRecently)
            cache.playlist.maxAge.multipliedBy(2).coerceAtMost(maxAgeMax)
        } else {
            YouTubePlaylistTable.MAX_AGE_DEFAULT
        }
        dao.setPlaylistItems(id = id, maxAge = maxAge, items = items)
    }

    override suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
        return fetchByIds(ids) { findVideosById(it) }.flatten()
    }

    override suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, true, Instant.now() + EXPIRATION_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        dao.addFreeChatItems(ids, false, Instant.now() + EXPIRATION_DEFAULT)
    }

    suspend fun addVideo(video: Collection<YouTubeVideo>) = withContext(Dispatchers.IO) {
        val current = Instant.now()
        val defaultExpiredAt = current + EXPIRATION_DEFAULT
        val v = dao.findFreeChatItems(video.map { it.id }).associateBy { it.videoId }
        val expiring = video.associateWith {
            when {
                it.isFreeChat == true || v[it.id]?.isFreeChat == true -> current + EXPIRATION_FREE_CHAT
                it.isUpcoming() -> defaultExpiredAt.coerceAtMost(checkNotNull(it.scheduledStartDateTime))
                it.isNowOnAir() -> current + EXPIRATION_ON_AIR
                it.isArchived -> EXPIRATION_MAX
                else -> defaultExpiredAt
            }
        }
        dao.addVideos(expiring)
    }

    suspend fun cleanUp() {
        removeNotExistVideos()
        dao.removeAllChannelLogs()
    }

    private suspend fun removeNotExistVideos() {
        val removingId = dao.findUnusedVideoIds()
        removeVideo(removingId)
    }

    suspend fun removeVideo(ids: Collection<YouTubeVideo.Id>) =
        withContext(Dispatchers.IO) {
            fetchByIds(ids) { removeVideos(ids) }
        }

    suspend fun findAllUnfinishedVideos(): List<YouTubeVideo> {
        return dao.findAllUnfinishedVideoList()
    }

    suspend fun fetchChannelList(ids: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
        return dao.findChannelDetail(ids)
    }

    suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>) {
        dao.addChannelDetails(channelDetail)
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    fun addChannelSection(channelSection: List<YouTubeChannelSection>) {
        channelSections[channelSection[0].channelId] = channelSection
    }

    private suspend fun <I : YouTubeId, O> fetchByIds(
        ids: Collection<I>,
        query: suspend YouTubeDao.(Collection<I>) -> O,
    ): List<O> {
        return if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size < 50) {
            listOf(dao.query(ids))
        } else {
            val a = database.withTransaction {
                ids.chunked(50).map { dao.query(it) }
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
