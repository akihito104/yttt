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
import com.freshdigitable.yttt.data.model.YouTubeVideoDetail
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoTable
import com.freshdigitable.yttt.data.source.local.db.toDbEntity
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
            val channels = subscriptions.map { it.channel }.toSet()
                .map { it.toDbEntity() }
            database.withTransaction {
                dao.addChannels(channels)
                dao.addSubscriptions(subscriptions.map { it.toDbEntity() })
            }
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
        val channels = channelLogs.map { it.channelId }.distinct()
            .filter { dao.findChannel(it) == null }
            .map { YouTubeChannelTable(id = it) }
        val videos = channelLogs.distinctBy { it.videoId }
            .filter { dao.findVideosById(listOf(it.videoId)).isEmpty() }
            .map {
                YouTubeVideoTable(
                    id = it.videoId,
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        database.withTransaction {
            dao.addChannels(channels)
            dao.addVideos(videos)
            dao.addChannelLogs(channelLogs.map { it.toDbEntity() })
        }
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
            database.withTransaction {
                dao.addPlaylist(YouTubePlaylistTable.createWithMaxAge(id))
                dao.removePlaylistItemsByPlaylistId(id)
            }
            return
        }
        check(items.all { it.playlistId == id })
        val cache = dao.findPlaylistById(id)
        if (cache == null) {
            database.withTransaction {
                dao.addPlaylist(YouTubePlaylistTable(id))
                dao.addPlaylistItems(items.map { it.toDbEntity() })
            }
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
        database.withTransaction {
            dao.updatePlaylist(id, maxAge = maxAge)
            dao.removePlaylistItemsByPlaylistId(id)
            dao.addPlaylistItems(items.map { it.toDbEntity() })
        }
    }

    override suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
        return fetchListByIds(ids) { youtubeDao.findVideosById(it) }
    }

    override suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = true) }
        dao.addFreeChatItems(f)
    }

    override suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = false) }
        dao.addFreeChatItems(f)
    }

    suspend fun addVideo(video: Collection<YouTubeVideo>) = withContext(Dispatchers.IO) {
        val current = Instant.now()
        val defaultExpiredAt = current + EXPIRATION_DEFAULT
        val expiring = video.map {
            val expired = when {
                it.isFreeChat == true -> current + EXPIRATION_FREE_CHAT
                it.isUpcoming() -> defaultExpiredAt.coerceAtMost(checkNotNull(it.scheduledStartDateTime))
                it.isNowOnAir() -> current + EXPIRATION_ON_AIR
                it.isArchived -> EXPIRATION_MAX
                else -> defaultExpiredAt
            }
            YouTubeVideoExpireTable(it.id, expired)
        }
        val videos = video.map { it.toDbEntity() }
        database.withTransaction {
            dao.addVideos(videos)
            dao.addLiveVideoExpire(expiring)
        }
    }

    private val videoDetailCache = mutableMapOf<YouTubeVideo.Id, YouTubeVideoDetail>()
    suspend fun fetchVideoDetail(id: YouTubeVideo.Id): YouTubeVideoDetail? {
        return videoDetailCache[id]
    }

    suspend fun addVideoDetail(detail: Collection<YouTubeVideoDetail>) {
        if (detail.isEmpty()) {
            return
        }
        videoDetailCache.putAll(detail.map { it.id to it })
    }

    suspend fun removeVideoDetail(id: YouTubeVideo.Id) {
        videoDetailCache.remove(id)
    }

    suspend fun cleanUp() {
        removeNotExistVideos()
        dao.removeAllChannelLogs()
    }

    private suspend fun removeNotExistVideos() {
        val removingId = dao.findUnusedVideoIds()
        removeVideo(removingId)
    }

    private suspend fun removeVideo(ids: Collection<YouTubeVideo.Id>) =
        withContext(Dispatchers.IO) {
            ids.forEach { removeVideoDetail(it) }
            fetchByIds(ids) {
                youtubeDao.removeFreeChatItems(it)
                youtubeDao.removeLiveVideoExpire(it)
                youtubeDao.removeVideos(it)
            }
        }

    suspend fun findAllUnfinishedVideos(): List<YouTubeVideo> {
        return dao.findAllUnfinishedVideoList()
    }

    suspend fun updateVideosInvisible(removed: Collection<YouTubeVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        dao.updateVideoInvisible(removed)
    }

    suspend fun fetchChannelList(ids: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
        return dao.findChannelDetail(ids)
    }

    suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>) {
        val channels = channelDetail.map { it.toDbEntity() }
        val additions = channelDetail.map { it.toAddition() }
        val playlists = additions.mapNotNull { it.uploadedPlayList }
            .distinct()
            .map { YouTubePlaylistTable(it) }
        database.withTransaction {
            dao.addChannels(channels)
            dao.addPlaylists(playlists)
            dao.addChannelAddition(additions)
        }
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    fun addChannelSection(channelSection: List<YouTubeChannelSection>) {
        channelSections[channelSection[0].channelId] = channelSection
    }

    private suspend fun <I : YouTubeId, E> fetchListByIds(
        ids: Collection<I>,
        query: suspend AppDatabase.(Collection<I>) -> List<E>,
    ): List<E> {
        return if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size < 50) {
            query(database, ids)
        } else {
            database.withTransaction {
                ids.chunked(50).map { query(database, it) }.flatten()
            }
        }
    }

    private suspend fun <I : YouTubeId> fetchByIds(
        ids: Collection<I>,
        query: suspend AppDatabase.(Collection<I>) -> Unit,
    ) {
        if (ids.isEmpty()) {
            return
        } else if (ids.size < 50) {
            query(database, ids)
        } else {
            database.withTransaction {
                ids.chunked(50).map { query(database, it) }
            }
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

private fun YouTubeSubscription.toDbEntity(): YouTubeSubscriptionTable = YouTubeSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id,
)

private fun YouTubeChannel.toDbEntity(): YouTubeChannelTable = YouTubeChannelTable(
    id = id, title = title, iconUrl = iconUrl,
)

private fun YouTubeVideo.toDbEntity(): YouTubeVideoTable = YouTubeVideoTable(
    id = id,
    title = title,
    channelId = channel.id,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    thumbnailUrl = thumbnailUrl,
)

private fun YouTubeChannelLog.toDbEntity(): YouTubeChannelLogTable = YouTubeChannelLogTable(
    id = id,
    dateTime = dateTime,
    videoId = videoId,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
)

private fun YouTubeChannelDetail.toAddition(): YouTubeChannelAdditionTable =
    YouTubeChannelAdditionTable(
        id = id,
        bannerUrl = bannerUrl,
        uploadedPlayList = uploadedPlayList,
        description = description,
        customUrl = customUrl,
        isSubscriberHidden = isSubscriberHidden,
        keywordsRaw = keywords.joinToString(","),
        publishedAt = publishedAt,
        subscriberCount = subscriberCount,
        videoCount = videoCount,
        viewsCount = viewsCount,
    )
