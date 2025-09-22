package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface YouTubeDataSource :
    YouTubeChannelDataSource,
    YouTubeVideoDataSource,
    YouTubeSubscriptionDataSource,
    YouTubePlaylistDataSource {
    companion object {
        // https://developers.google.com/youtube/v3/docs/videos/list#parameters
        const val MAX_BATCH_SIZE = 50
        const val MAX_PAGE_SIZE = 50
    }

    suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>>
    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): Result<List<YouTubeChannelLog>>

    interface Local :
        YouTubeDataSource,
        YouTubeVideoDataSource.Local,
        YouTubePlaylistDataSource.Local,
        YouTubeChannelDataSource.Local,
        YouTubeSubscriptionDataSource.Local,
        Extended,
        ImageDataSource {
        suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>)
        suspend fun addChannelLogs(channelLogs: Collection<YouTubeChannelLog>)
    }

    interface Extended :
        YouTubeVideoDataSource.Extended,
        YouTubeSubscriptionDataSource.Extended,
        YouTubePlaylistDataSource.Extended {
        suspend fun cleanUp()
        suspend fun deleteAllTables()
    }

    interface Remote :
        YouTubeDataSource,
        YouTubeChannelDataSource.Remote,
        YouTubeVideoDataSource.Remote,
        YouTubeSubscriptionDataSource.Remote,
        YouTubePlaylistDataSource.Remote
}

interface YouTubeChannelDataSource {
    suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannel>>
    suspend fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<YouTubeChannelRelatedPlaylist>>

    suspend fun fetchChannelDetailList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<Updatable<YouTubeChannelDetail>>>

    interface Local : YouTubeChannelDataSource {
        suspend fun addChannelList(channel: Collection<YouTubeChannel>)
        suspend fun addChannelRelatedPlaylists(channel: List<YouTubeChannelRelatedPlaylist>)
        suspend fun addChannelDetailList(channelDetail: Collection<Updatable<YouTubeChannelDetail>>)
    }

    interface Remote : YouTubeChannelDataSource
}

interface YouTubeVideoDataSource {
    interface Local : YouTubeVideoDataSource
    interface Extended {
        val videos: Flow<List<YouTubeVideoExtended>>
        suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>>
        suspend fun fetchUpdatableVideoIds(current: Instant): List<YouTubeVideo.Id>
        suspend fun addVideo(video: Collection<Updatable<YouTubeVideoExtended>>)
        suspend fun updateAsArchivedVideo(ids: Set<YouTubeVideo.Id>)
        suspend fun removeVideo(ids: Set<YouTubeVideo.Id>)

        suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>)
        suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>)
    }

    interface Remote : YouTubeVideoDataSource {
        suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideo>>>
    }
}

interface YouTubeSubscriptionDataSource {
    suspend fun fetchPagedSubscription(
        query: YouTubeSubscriptionQuery,
    ): Result<NetworkResponse<List<YouTubeSubscription>>>

    interface Local : YouTubeSubscriptionDataSource {
        suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id>
    }

    interface Extended {
        var subscriptionsFetchedAt: Instant
        var subscriptionsRelevanceOrderedFetchedAt: Instant
        suspend fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummary>
        suspend fun findSubscriptionSummariesByOffset(offset: Int, pageSize: Int): List<YouTubeSubscriptionSummary>

        suspend fun addPagedSubscription(subscription: Collection<YouTubeSubscription>)
        suspend fun findSubscriptionQuery(offset: Int): YouTubeSubscriptionQuery?
        suspend fun syncSubscriptionList(
            subscriptions: Set<YouTubeSubscription.Id>,
            query: List<YouTubeSubscriptionQuery>,
        )
    }

    interface Remote : YouTubeSubscriptionDataSource
}

interface YouTubePlaylistDataSource {
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

    interface Local : YouTubePlaylistDataSource {
        suspend fun addPlaylist(playlist: Collection<Updatable<YouTubePlaylist>>)
    }

    interface Extended {
        suspend fun updatePlaylistWithItems(item: YouTubePlaylistWithItem<*>, cacheControl: CacheControl)
        suspend fun updatePlaylistWithItemsCacheControl(item: YouTubePlaylistWithItem<*>, cacheControl: CacheControl)
    }

    interface Remote : YouTubePlaylistDataSource {
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
