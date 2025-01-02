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
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val remoteSource: YoutubeDataSource.Remote,
    private val localSource: YoutubeDataSource.Local,
    private val dateTimeProvider: DateTimeProvider,
    coroutineScope: CoroutineScope,
) : YoutubeDataSource {
    val videos: StateFlow<List<YouTubeVideoExtended>> = localSource.videos
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> {
        val res = remoteSource.fetchAllSubscribe(maxResult)
        val current = localSource.fetchAllSubscribe()
        val deleted = current.map { it.id }.toSet() - res.map { it.id }.toSet()
        localSource.removeSubscribes(deleted)
        localSource.addSubscribes(res)
        return res
    }

    suspend fun fetchPagedSubscriptionSummary(): Flow<List<YouTubeSubscriptionSummary>> {
        val cache = localSource.fetchAllSubscribe()
        return flow {
            val res = mutableListOf<YouTubeSubscriptionSummary>()
            val subs = remoteSource.fetchAllSubscribePaged()
                .fold(emptyList<YouTubeSubscription>()) { acc, value ->
                    val page = value - acc.toSet()
                    localSource.addSubscribes(page)
                    val summary = localSource.findSubscriptionSummaries(page.map { it.id })
                    val needsChannelDetail = summary.filter { it.uploadedPlaylistId == null }
                    if (needsChannelDetail.isEmpty()) {
                        emit(res + summary)
                        res.addAll(summary)
                    } else {
                        fetchChannelList(needsChannelDetail.map { it.channelId }.toSet())
                        val updated = needsChannelDetail.map { it.subscriptionId }.toSet()
                        val s = summary.associateBy { it.subscriptionId }.toMutableMap().apply {
                            localSource.findSubscriptionSummaries(updated).forEach {
                                this[it.subscriptionId] = it
                            }
                        }.values
                        check((page.map { it.id } - s.map { it.subscriptionId }.toSet()).isEmpty())
                        emit(res + s)
                        res.addAll(s)
                    }
                    value
                }
            val deleted = cache.map { it.id } - subs.map { it.id }.toSet()
            localSource.removeSubscribes(deleted.toSet())
        }
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

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideo> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val cache = localSource.fetchVideoList(ids)
        val neededId = ids - cache.map { it.id }.toSet()
        if (neededId.isEmpty()) {
            return cache
        }
        val res = remoteSource.fetchVideoList(neededId)
        return cache + res
    }

    override suspend fun addVideo(video: Collection<YouTubeVideoExtended>) {
        localSource.addVideo(video)
    }

    fun removeImageByUrl(url: Collection<String>) {
        localSource.removeImageByUrl(url)
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
        if (items == null) {
            localSource.setPlaylistItemsByPlaylistId(id, null)
            return
        }
        val uploadedAtAnotherChannel = items
            .filter { it.channel.id != it.videoOwnerChannelId }
            .mapNotNull { it.videoOwnerChannelId }
        if (uploadedAtAnotherChannel.isNotEmpty()) {
            fetchChannelList(uploadedAtAnotherChannel.toSet())
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

    suspend fun removeVideo(removed: Set<YouTubeVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        localSource.removeVideo(removed)
    }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
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

    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        val cache = localSource.fetchChannelSection(id)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelSection(id)
        localSource.addChannelSection(remote)
        return remote
    }

    suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<YouTubePlaylist> {
        return remoteSource.fetchPlaylist(ids)
    }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        localSource.addFreeChatItems(ids)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        localSource.removeFreeChatItems(ids)
    }

    suspend fun deleteAllTables() {
        localSource.deleteAllTables()
    }

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
