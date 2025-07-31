package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
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
        const val MAX_PAGE_SIZE = 50
    }

    fun fetchSubscriptions(pageSize: Int = MAX_PAGE_SIZE): Flow<Result<YouTubeSubscriptions>>
    suspend fun fetchPagedSubscription(
        pageSize: Int = MAX_PAGE_SIZE,
        nextPageToken: String?,
        eTag: String?,
    ): Result<NetworkResponse<List<YouTubeSubscription>>>

    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): Result<List<YouTubeChannelLog>>

    suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<Updatable<YouTubeChannelDetail>>>
    suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>>

    suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>>
    suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>? = null,
    ): Result<Updatable<YouTubePlaylistWithItems>?>

    suspend fun fetchPlaylistWithItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>? = null,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>?>

    interface Local : YouTubeDataSource, YouTubeLiveDataSource, ImageDataSource {
        suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id>

        suspend fun addPlaylist(playlist: Collection<Updatable<YouTubePlaylist>>)

        suspend fun addChannelList(channelDetail: Collection<Updatable<YouTubeChannelDetail>>)
        suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>)
        suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>)
    }

    interface Remote : YouTubeDataSource {
        override fun fetchSubscriptions(pageSize: Int): Flow<Result<YouTubeSubscriptions.Paged>>
        suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideo>>>
        override suspend fun fetchPlaylistWithItems(
            id: YouTubePlaylist.Id,
            maxResult: Long,
            cache: YouTubePlaylistWithItem<*>?,
        ): Result<Updatable<YouTubePlaylistWithItems>>

        override suspend fun fetchPlaylistWithItemDetails(
            id: YouTubePlaylist.Id,
            maxResult: Long,
            cache: YouTubePlaylistWithItem<*>?,
        ): Result<Updatable<YouTubePlaylistWithItemDetails>>
    }
}

interface YouTubeLiveDataSource {
    val videos: Flow<List<YouTubeVideoExtended>>
    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>>
    suspend fun addVideo(video: Collection<Updatable<YouTubeVideoExtended>>)
    suspend fun removeVideo(ids: Set<YouTubeVideo.Id>)

    suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>)

    var subscriptionsFetchedAt: Instant
    suspend fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummary>
    suspend fun findSubscriptionSummariesByOffset(
        offset: Int,
        pageSize: Int,
    ): List<YouTubeSubscriptionSummary>

    suspend fun addSubscribes(subscriptions: YouTubeSubscriptions)
    suspend fun addPagedSubscription(
        subscription: Collection<YouTubeSubscription>,
        fetchedAt: Instant?,
    )

    suspend fun addSubscriptionEtag(offset: Int, nextPageToken: String?, eTag: String)
    suspend fun findSubscriptionQuery(offset: Int): YouTubeSubscriptionQuery?
    suspend fun removeSubscribesByRemainingIds(subscriptions: Set<YouTubeSubscription.Id>)

    suspend fun updatePlaylistWithItems(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    )

    suspend fun updatePlaylistWithItemsCacheControl(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    )

    suspend fun fetchUpdatableVideoIds(current: Instant): List<YouTubeVideo.Id>

    suspend fun cleanUp()
    suspend fun deleteAllTables()
}
