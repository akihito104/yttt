package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
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
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface YouTubeDataSource {
    companion object {
        // https://developers.google.com/youtube/v3/docs/videos/list#parameters
        const val MAX_BATCH_SIZE = 50
        const val MAX_PAGE_SIZE: Long = 50
    }

    fun fetchSubscriptions(pageSize: Long = MAX_PAGE_SIZE): Flow<Result<YouTubeSubscriptions>>
    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): Result<List<YouTubeChannelLog>>

    suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<Updatable<YouTubeChannelDetail>>>
    suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>>

    suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>>
    suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<Updatable<List<YouTubePlaylistItem>>>

    suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>? = null,
    ): Result<Updatable<YouTubePlaylistWithItems>?>

    interface Local : YouTubeDataSource, YouTubeLiveDataSource, ImageDataSource {
        suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id>

        suspend fun addPlaylist(playlist: Collection<Updatable<YouTubePlaylist>>)
        suspend fun fetchPlaylistItemSummary(
            playlistId: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemSummary>

        suspend fun addChannelList(channelDetail: Collection<Updatable<YouTubeChannelDetail>>)
        suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>)
        suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>)
    }

    interface Remote : YouTubeDataSource {
        override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions.Paged>>
        suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideo>>>
        override suspend fun fetchPlaylistWithItems(
            id: YouTubePlaylist.Id,
            maxResult: Long,
            cache: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>?,
        ): Result<Updatable<YouTubePlaylistWithItems>>
    }
}

interface YouTubeLiveDataSource {
    val videos: Flow<List<YouTubeVideoExtended>>
    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>>
    suspend fun addVideo(video: Collection<Updatable<YouTubeVideoExtended>>)
    suspend fun removeVideo(ids: Set<YouTubeVideo.Id>)

    suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>)

    val subscriptionsFetchedAt: Instant
    suspend fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummary>
    suspend fun addSubscribes(subscriptions: YouTubeSubscriptions)
    suspend fun removeSubscribes(subscriptions: Set<YouTubeSubscription.Id>)

    suspend fun updatePlaylistWithItems(updatable: Updatable<YouTubePlaylistWithItems>)
    suspend fun fetchPlaylistWithItemSummaries(id: YouTubePlaylist.Id): YouTubePlaylistWithItemSummaries?
    suspend fun fetchUpdatableVideoIds(current: Instant): List<YouTubeVideo.Id>

    suspend fun cleanUp()
    suspend fun deleteAllTables()
}
