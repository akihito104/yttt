package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val remoteSource: YoutubeDataSource.Remote,
    private val localSource: YoutubeDataSource.Local,
    private val dateTimeProvider: DateTimeProvider,
) : YoutubeDataSource {
    val videos: Flow<List<YouTubeVideo>> = localSource.videos

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> {
        val res = remoteSource.fetchAllSubscribe(maxResult)
        val current = localSource.fetchAllSubscribe()
        val deleted = res.map { it.id }.toSet() - current.map { it.id }.toSet()
        localSource.removeSubscribes(deleted)
        localSource.addSubscribes(res)
        return res
    }

    suspend fun fetchAllSubscribeSummary(): List<YouTubeSubscriptionSummary> {
        fetchAllSubscribe(50)
        val summary = localSource.fetchAllSubscriptionSummary()
        val needsChannelDetail = summary.filter { it.uploadedPlaylistId == null }
        if (needsChannelDetail.isEmpty()) {
            return summary
        }
        fetchChannelList(needsChannelDetail.map { it.channelId })
        return localSource.fetchAllSubscriptionSummary()
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

    override suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val cache = localSource.fetchVideoList(ids)
        val neededId = ids - cache.map { it.id }.toSet()
        if (neededId.isEmpty()) {
            return cache
        }
        val res = remoteSource.fetchVideoList(neededId)
        localSource.addVideo(res)
        return localSource.fetchVideoList(ids)
    }

    suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long = 10,
    ): List<YouTubePlaylistItem> {
        val cache = localSource.fetchPlaylistItems(id)
        if (cache != null) {
            return cache
        }
        updatePlaylistItemCache(id, maxResult)
        return localSource.fetchPlaylistItems(id) ?: return emptyList()
    }

    private suspend fun updatePlaylistItemCache(id: YouTubePlaylist.Id, maxResult: Long) {
        val items = remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
        val uploadedAtAnotherChannel = items
            .filter { it.channel.id != it.videoOwnerChannelId }
            .mapNotNull { it.videoOwnerChannelId }
            .toSet()
        if (uploadedAtAnotherChannel.isNotEmpty()) {
            fetchChannelList(uploadedAtAnotherChannel)
        }
        localSource.setPlaylistItemsByPlaylistId(id, items)
    }

    suspend fun fetchPlaylistItemSummaries(
        summary: YouTubeSubscriptionSummary,
        current: Instant = dateTimeProvider.now(),
        maxResult: Long = 10,
    ): List<YouTubePlaylistItemSummary> {
        val playlistId = checkNotNull(summary.uploadedPlaylistId)
        if (summary.needsUpdatePlaylist(current)) {
            updatePlaylistItemCache(playlistId, maxResult)
        }
        return localSource.fetchPlaylistItemSummary(playlistId, maxResult)
    }

    suspend fun cleanUp() {
        localSource.cleanUp()
    }

    suspend fun findAllUnfinishedVideos(): List<YouTubeVideo> {
        return localSource.findAllUnfinishedVideos()
    }

    suspend fun removeVideo(removed: Collection<YouTubeVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        localSource.removeVideo(removed)
    }

    suspend fun fetchChannelList(ids: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val cache = localSource.fetchChannelList(ids)
        val needed = ids - cache.map { it.id }.toSet()
        if (needed.isEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelList(needed)
        localSource.addChannelList(remote)
        return remote
    }

    suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        val cache = localSource.fetchChannelSection(id)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelSection(id)
        localSource.addChannelSection(remote)
        return remote
    }

    suspend fun fetchPlaylist(ids: Collection<YouTubePlaylist.Id>): List<YouTubePlaylist> {
        return remoteSource.fetchPlaylist(ids)
    }

    override suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        localSource.addFreeChatItems(ids)
    }

    override suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        localSource.removeFreeChatItems(ids)
    }

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
