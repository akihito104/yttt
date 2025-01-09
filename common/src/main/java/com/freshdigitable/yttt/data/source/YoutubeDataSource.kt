package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface YoutubeDataSource {
    suspend fun fetchAllSubscribe(maxResult: Long = 30): List<YouTubeSubscription>
    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): List<YouTubeChannelLog>

    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideo>
    suspend fun addVideo(video: Collection<YouTubeVideoExtendedUpdatable>)
    suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>)
    suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): List<YouTubeChannelDetail>
    suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection>

    interface Local : YoutubeDataSource, ImageDataSource {
        val videos: Flow<List<YouTubeVideoExtendedUpdatable>>
        suspend fun removeSubscribes(subscriptions: Set<YouTubeSubscription.Id>)
        suspend fun addSubscribes(subscriptions: Collection<YouTubeSubscription>)
        suspend fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummary>
        suspend fun addLiveChannelLogs(channelLogs: Collection<YouTubeChannelLog>)
        suspend fun fetchPlaylistItems(id: YouTubePlaylist.Id): List<YouTubePlaylistItem>?
        suspend fun setPlaylistItemsByPlaylistId(
            id: YouTubePlaylist.Id,
            items: Collection<YouTubePlaylistItem>?,
        )

        suspend fun fetchPlaylistItemSummary(
            playlistId: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemSummary>

        suspend fun cleanUp()
        override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideoExtendedUpdatable>
        suspend fun removeVideo(ids: Set<YouTubeVideo.Id>)
        suspend fun addChannelList(channelDetail: Collection<YouTubeChannelDetail>)
        suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>)
        suspend fun deleteAllTables()
    }

    interface Remote : YoutubeDataSource {
        suspend fun fetchAllSubscribePaged(pageSize: Int = 50): Flow<List<YouTubeSubscription>>

        suspend fun fetchPlaylistItems(
            id: YouTubePlaylist.Id,
            maxResult: Long = 20,
            pageToken: String? = null,
        ): List<YouTubePlaylistItem>?

        suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<YouTubePlaylist>

        override suspend fun addVideo(video: Collection<YouTubeVideoExtendedUpdatable>) =
            throw UnsupportedOperationException()

        override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) =
            throw UnsupportedOperationException()

        override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) =
            throw UnsupportedOperationException()
    }
}
