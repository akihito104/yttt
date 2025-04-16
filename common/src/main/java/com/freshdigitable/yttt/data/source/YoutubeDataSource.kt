package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemSummaries
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface YoutubeDataSource {
    suspend fun fetchAllSubscribe(maxResult: Long = 30): Result<List<YouTubeSubscription>>
    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): List<YouTubeChannelLog>

    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideo>>
    suspend fun addVideo(video: Collection<YouTubeVideoExtended>)
    suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannelDetail>>
    suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection>
    suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<YouTubePlaylist>

    interface Local : YoutubeDataSource, ImageDataSource {
        val videos: Flow<List<YouTubeVideoExtended>>
        suspend fun removeSubscribes(subscriptions: Set<YouTubeSubscription.Id>)
        suspend fun addSubscribes(subscriptions: Collection<YouTubeSubscription>)
        suspend fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummary>
        suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>)
        suspend fun fetchPlaylistWithItems(id: YouTubePlaylist.Id): YouTubePlaylistWithItems?
        suspend fun fetchPlaylistWithItemSummaries(id: YouTubePlaylist.Id): YouTubePlaylistWithItemSummaries?
        suspend fun updatePlaylistWithItems(updatable: YouTubePlaylistWithItems)
        suspend fun addPlaylist(playlist: Collection<YouTubePlaylist>)

        suspend fun fetchPlaylistItemSummary(
            playlistId: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemSummary>

        suspend fun cleanUp()
        override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideoExtended>>
        suspend fun removeVideo(ids: Set<YouTubeVideo.Id>)
        suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>)
        suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>)
        suspend fun deleteAllTables()
    }

    interface Remote : YoutubeDataSource {
        suspend fun fetchAllSubscribePaged(pageSize: Int = 50): Flow<Result<List<YouTubeSubscription>>>

        suspend fun fetchPlaylistItems(
            id: YouTubePlaylist.Id,
            maxResult: Long = 20,
            pageToken: String? = null,
        ): List<YouTubePlaylistItem>?

        override suspend fun addVideo(video: Collection<YouTubeVideoExtended>) =
            throw UnsupportedOperationException()

        override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) =
            throw UnsupportedOperationException()

        override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) =
            throw UnsupportedOperationException()
    }
}

@Singleton
class IoScope @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun <T> asResult(block: suspend CoroutineScope.() -> T): Result<T> =
        withContext(ioDispatcher) { runCatching { block(this) } }

    fun <T> asResultFlow(block: suspend FlowCollector<Result<T>>.() -> Unit): Flow<Result<T>> =
        flow {
            runCatching { block(this) }.onFailure { emit(Result.failure<T>(it)) }
        }.flowOn(ioDispatcher)
}
