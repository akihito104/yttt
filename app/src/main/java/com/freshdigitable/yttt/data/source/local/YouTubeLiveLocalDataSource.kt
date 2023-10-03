package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.freshdigitable.yttt.data.source.local.LivePlaylistCache.Companion.updateCache
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelTable
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.LiveVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.LiveVideoTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveLocalDataSource @Inject constructor(
    private val database: AppDatabase,
) : YoutubeLiveDataSource {
    val subscriptions: Flow<List<LiveSubscription>> = database.dao.watchAllSubscriptions()
    val videos: Flow<List<LiveVideo>> = database.dao.watchAllUnfinishedVideos()

    override suspend fun fetchAllSubscribe(maxResult: Long): List<LiveSubscription> =
        withContext(Dispatchers.IO) {
            database.dao.findAllSubscriptions()
        }

    suspend fun addSubscribes(subscriptions: Collection<LiveSubscription>) =
        withContext(Dispatchers.IO) {
            val channels = subscriptions.map { it.channel }.toSet()
                .map { it.toDbEntity() }
            database.withTransaction {
                database.dao.addChannels(channels)
                database.dao.addSubscriptions(subscriptions.map { it.toDbEntity() })
            }
        }

    suspend fun removeSubscribes(subscriptions: Collection<LiveSubscription.Id>) {
        database.dao.removeSubscriptions(subscriptions)
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<LiveChannelLog> {
        if (publishedAfter != null) {
            return database.dao.findChannelLogs(channelId, publishedAfter, maxResult)
        }
        return database.dao.findChannelLogs(channelId, maxResult)
    }

    suspend fun addLiveChannelLogs(channelLogs: List<LiveChannelLog>) {
        val channels = channelLogs.map { it.channelId }.distinct()
            .filter { database.dao.findChannel(it) == null }
            .map { LiveChannelTable(id = it) }
        val videos = channelLogs.distinctBy { it.videoId }
            .filter { database.dao.findVideosById(listOf(it.videoId)).isEmpty() }
            .map {
                LiveVideoTable(
                    id = it.videoId,
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        database.withTransaction {
            database.dao.addChannels(channels)
            database.dao.addVideos(videos)
            database.dao.addChannelLogs(channelLogs.map { it.toDbEntity() })
        }
    }

    private val playlistExpireTable = mutableMapOf<LivePlaylist.Id, LivePlaylistCache>()
    suspend fun fetchPlaylistItems(id: LivePlaylist.Id): List<LivePlaylistItem> {
        val cache = playlistExpireTable[id] ?: return emptyList()
        if (cache.isExpired()) {
            return cache.playlistItems.toList()
        }
        return emptyList()
    }

    suspend fun setPlaylistItemsByPlaylistId(
        id: LivePlaylist.Id,
        items: Collection<LivePlaylistItem>,
    ) {
        if (items.isEmpty()) {
            return
        }
        check(items.all { it.playlistId == id })
        val cache = playlistExpireTable[id]
        playlistExpireTable[id] = cache.updateCache(id, items)
    }

    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        return database.dao.findVideosById(ids)
    }

    override suspend fun addFreeChatItems(ids: Collection<LiveVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = true) }
        database.dao.addFreeChatItems(f)
    }

    override suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = false) }
        database.dao.addFreeChatItems(f)
    }

    suspend fun addVideo(video: Collection<LiveVideo>) = withContext(Dispatchers.IO) {
        val current = Instant.now()
        val defaultExpiredAt = current + EXPIRATION_DEFAULT
        val expiring = video.map {
            val expired = when {
                it.isFreeChat == true -> current + EXPIRATION_FREE_CHAT
                it.isUpcoming() -> defaultExpiredAt.coerceAtMost(checkNotNull(it.scheduledStartDateTime))
                it.isNowOnAir() -> current
                it.isArchived -> EXPIRATION_MAX
                else -> defaultExpiredAt
            }
            LiveVideoExpireTable(it.id, expired)
        }
        val videos = video.map { it.toDbEntity() }
        database.withTransaction {
            database.dao.addVideos(videos)
            database.dao.addLiveVideoExpire(expiring)
        }
    }

    private val videoDetailCache = mutableMapOf<LiveVideo.Id, LiveVideoDetail>()
    suspend fun fetchVideoDetail(id: LiveVideo.Id): LiveVideoDetail? {
        return videoDetailCache[id]
    }

    suspend fun addVideoDetail(detail: Collection<LiveVideoDetail>) {
        if (detail.isEmpty()) {
            return
        }
        videoDetailCache.putAll(detail.map { it.id to it })
    }

    suspend fun removeVideoDetail(id: LiveVideo.Id) {
        videoDetailCache.remove(id)
    }

    suspend fun cleanUp(ids: Collection<LiveVideo.Id>) {
        removeNotExistVideos(ids)
        database.dao.removeAllChannelLogs()
    }

    private suspend fun removeNotExistVideos(ids: Collection<LiveVideo.Id>) {
        val removingId = database.dao.findNotExistVideoIds(ids)
        removeVideo(removingId)
    }

    private suspend fun removeVideo(ids: Collection<LiveVideo.Id>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.dao.removeFreeChatItems(ids)
            database.dao.removeLiveVideoExpire(ids)
            ids.forEach { removeVideoDetail(it) }
            database.dao.removeVideos(ids)
        }
    }

    suspend fun findAllUnfinishedVideos(): List<LiveVideo> {
        return database.dao.findAllUnfinishedVideoList()
    }

    suspend fun updateVideosInvisible(removed: Collection<LiveVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        database.dao.updateVideoInvisible(removed)
    }

    suspend fun fetchChannelList(ids: Collection<LiveChannel.Id>): List<LiveChannelDetail> {
        return database.dao.findChannelDetail(ids)
    }

    suspend fun addChannelList(channelDetail: Collection<LiveChannelDetail>) {
        val channels = channelDetail.map { it.toDbEntity() }
        val additions = channelDetail.map { it.toAddition() }
        database.withTransaction {
            database.dao.addChannels(channels)
            database.dao.addChannelAddition(additions)
        }
    }

    private val channelSections = mutableMapOf<LiveChannel.Id, List<LiveChannelSection>>()
    fun fetchChannelSection(id: LiveChannel.Id): List<LiveChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    fun addChannelSection(channelSection: List<LiveChannelSection>) {
        channelSections[channelSection[0].channelId] = channelSection
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
        private val LiveVideo.isArchived: Boolean
            get() = !isLiveStream() || actualEndDateTime != null
    }
}

private fun LiveSubscription.toDbEntity(): LiveSubscriptionTable = LiveSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id,
)

private fun LiveChannel.toDbEntity(): LiveChannelTable = LiveChannelTable(
    id = id, title = title, iconUrl = iconUrl,
)

private fun LiveVideo.toDbEntity(): LiveVideoTable = LiveVideoTable(
    id = id,
    title = title,
    channelId = channel.id,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    thumbnailUrl = thumbnailUrl,
)

private fun LiveChannelLog.toDbEntity(): LiveChannelLogTable = LiveChannelLogTable(
    id = id,
    dateTime = dateTime,
    videoId = videoId,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
)

private fun LiveChannelDetail.toAddition(): LiveChannelAdditionTable = LiveChannelAdditionTable(
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

data class LivePlaylistCache(
    val playlistId: LivePlaylist.Id,
    val playlistItems: Collection<LivePlaylistItem>,
    val modifiedAt: Instant = Instant.now(),
    val expireDuration: Duration = DEFAULT_DURATION,
) {
    private val expiredAt: Instant get() = modifiedAt + expireDuration
    fun isExpired(current: Instant = Instant.now()): Boolean = expiredAt.isAfter(current)
    private fun updateExpireDuration(
        items: Collection<LivePlaylistItem>,
        current: Instant = Instant.now(),
    ): LivePlaylistCache {
        val cachedIds = playlistItems.map { it.id }.toSet()
        val newIds = items.map { it.id }.toSet()
        val isNotModified = (cachedIds - newIds).isEmpty() && (newIds - cachedIds).isEmpty()
        val nextDuration = if (isNotModified) {
            expireDuration.multipliedBy(2).coerceAtMost(Duration.ofDays(1))
        } else {
            DEFAULT_DURATION
        }
        return LivePlaylistCache(playlistId, playlistItems, current, nextDuration)
    }

    companion object {
        private val DEFAULT_DURATION = Duration.ofMinutes(10)
        fun LivePlaylistCache?.updateCache(
            id: LivePlaylist.Id,
            items: Collection<LivePlaylistItem>,
            current: Instant = Instant.now(),
        ): LivePlaylistCache =
            this?.updateExpireDuration(items, current) ?: LivePlaylistCache(id, items, current)
    }
}
