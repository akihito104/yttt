package com.freshdigitable.yttt.data.source.remote

import android.util.Log
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelLogEntity
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LivePlaylistItemEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.ChannelSection
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveRemoteDataSource @Inject constructor(
    accountRepository: AccountRepository,
) : YoutubeLiveDataSource {
    private val youtube = YouTube.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance()
    ) {
        Log.d("RemoteDataSource", "init: ${it.url}")
        accountRepository.credential.initialize(it)
    }.build()

    override suspend fun fetchAllSubscribe(
        maxResult: Long,
    ): List<LiveSubscription> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.subscriptions().list(listOf(PART_SNIPPET))
                    .setMine(true)
                    .setMaxResults(maxResult)
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        ).mapIndexed { i, s -> s.toLiveSubscription(i) }
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<LiveChannelLog> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.activities().list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                    .setChannelId(channelId.value).apply {
                        if (maxResult != null) {
                            this.maxResults = maxResult
                        }
                        if (publishedAfter != null) {
                            setPublishedAfter(publishedAfter.toString())
                        }
                    }
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        ).filter { it.contentDetails?.upload != null }
            .map { it.toChannelLog() }
    }

    override suspend fun fetchVideoList(
        ids: Collection<LiveVideo.Id>,
    ): List<LiveVideoDetail> = withContext(Dispatchers.IO) {
        ids.map { it.value }.chunked(VIDEO_MAX_FETCH_SIZE).flatMap {
            youtube.videos().list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(it)
                .execute()
                .items
        }.map { it.toLiveVideo() }
    }

    suspend fun fetchChannelList(
        ids: Collection<LiveChannel.Id>,
    ): List<LiveChannelDetail> = withContext(Dispatchers.IO) {
        ids.map { it.value }.chunked(VIDEO_MAX_FETCH_SIZE).flatMap {
            youtube.channels().list(
                listOf(
                    PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics",
                )
            )
                .setId(it)
                .execute()
                .items
        }.map { LiveChannelImpl(it) }
    }

    suspend fun fetchChannelSection(
        channelId: LiveChannel.Id,
    ): List<LiveChannelSection> = withContext(Dispatchers.IO) {
        youtube.channelSections().list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setChannelId(channelId.value)
            .execute()
            .items
            .map { LiveChannelSectionImpl(it) }
    }

    suspend fun fetchPlaylistItems(
        id: LivePlaylist.Id,
        maxResult: Long = 20,
        pageToken: String? = null,
    ): List<LivePlaylistItem> = withContext(Dispatchers.IO) {
        youtube.playlistItems().list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .setPageToken(pageToken)
            .execute()
            .items
            .map { it.toLivePlaylistItem() }
    }

    private fun <T, E> fetchAllItems(
        fetcher: (String?) -> T,
        getItems: T.() -> List<E>,
        getNextToken: T.() -> String?,
    ): List<E> {
        var token: String? = null
        val res = mutableListOf<E>()
        do {
            val response = fetcher(token)
            res.addAll(response.getItems())
            token = response.getNextToken()
        } while (token != null)
        return res
    }

    companion object {
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"

        // https://developers.google.com/youtube/v3/docs/videos/list#parameters
        private const val VIDEO_MAX_FETCH_SIZE = 50
    }
}

private fun Subscription.toLiveSubscription(order: Int): LiveSubscription = LiveSubscriptionEntity(
    id = LiveSubscription.Id(id),
    subscribeSince = Instant.ofEpochMilli(snippet.publishedAt.value),
    channel = LiveChannelEntity(
        id = LiveChannel.Id(snippet.resourceId.channelId),
        iconUrl = snippet.thumbnails.url,
        title = snippet.title,
    ),
    order = order,
)

private fun Activity.toChannelLog(): LiveChannelLog = LiveChannelLogEntity(
    id = LiveChannelLog.Id(id),
    dateTime = Instant.ofEpochMilli(snippet.publishedAt.value),
    videoId = LiveVideo.Id(contentDetails.upload.videoId),
    channelId = LiveChannel.Id(snippet.channelId),
    thumbnailUrl = snippet.thumbnails.url,
)

private fun Video.toLiveVideo(): LiveVideoDetail =
    object : LiveVideoDetail, LiveVideo by LiveVideoEntity(
        id = LiveVideo.Id(id),
        channel = LiveChannelEntity(
            id = LiveChannel.Id(snippet.channelId),
            title = snippet.channelTitle,
            iconUrl = "",
        ),
        title = snippet.title,
        scheduledStartDateTime = liveStreamingDetails?.scheduledStartTime?.toInstant(),
        scheduledEndDateTime = liveStreamingDetails?.scheduledEndTime?.toInstant(),
        actualStartDateTime = liveStreamingDetails?.actualStartTime?.toInstant(),
        actualEndDateTime = liveStreamingDetails?.actualEndTime?.toInstant(),
        thumbnailUrl = this.snippet.thumbnails.url,
    ) {
        override val description: String
            get() = this@toLiveVideo.snippet.description
        override val viewerCount: BigInteger?
            get() = this@toLiveVideo.liveStreamingDetails?.concurrentViewers

        override fun toString(): String = this@toLiveVideo.toPrettyString()
    }

private fun DateTime.toInstant(): Instant = Instant.ofEpochMilli(value)
private val ThumbnailDetails.url: String
    get() = (maxres ?: high ?: standard ?: medium ?: default).url ?: ""

private data class LiveChannelImpl(
    private val channel: Channel,
) : LiveChannelDetail, LiveChannel by LiveChannelEntity(
    id = LiveChannel.Id(channel.id),
    title = channel.snippet.title,
    iconUrl = channel.snippet.thumbnails.url,
) {
    override val bannerUrl: String?
        get() = channel.brandingSettings?.image?.bannerExternalUrl
    override val subscriberCount: BigInteger
        get() = channel.statistics.subscriberCount
    override val isSubscriberHidden: Boolean
        get() = channel.statistics.hiddenSubscriberCount
    override val videoCount: BigInteger
        get() = channel.statistics.videoCount
    override val viewsCount: BigInteger
        get() = channel.statistics.viewCount
    override val customUrl: String
        get() = channel.snippet.customUrl
    override val keywords: Collection<String>
        get() = channel.brandingSettings?.channel?.keywords?.split(",", " ") ?: emptyList()
    override val publishedAt: Instant
        get() = channel.snippet.publishedAt.toInstant()
    override val description: String?
        get() = channel.brandingSettings?.channel?.description
    override val uploadedPlayList: LivePlaylist.Id?
        get() = channel.contentDetails?.relatedPlaylists?.uploads?.let { LivePlaylist.Id(it) }

    override fun toString(): String = channel.toPrettyString()
}

private data class LiveChannelSectionImpl(
    private val channelSection: ChannelSection
) : LiveChannelSection {
    override val channelId: LiveChannel.Id
        get() = LiveChannel.Id(channelSection.snippet.channelId)
    override val title: String
        get() = channelSection.snippet.title
    override val position: Long
        get() = channelSection.snippet.position

    override fun toString(): String {
        return channelSection.toPrettyString()
    }
}

private fun PlaylistItem.toLivePlaylistItem(): LivePlaylistItem =
    object : LivePlaylistItem by LivePlaylistItemEntity(
        id = LivePlaylistItem.Id(id),
        playlistId = LivePlaylist.Id(snippet.playlistId),
        title = snippet.title,
        thumbnailUrl = snippet.thumbnails.url,
        videoId = LiveVideo.Id(contentDetails.videoId),
        channel = LiveChannelEntity(
            id = LiveChannel.Id(snippet.channelId),
            title = snippet.channelTitle,
            iconUrl = "",
        ),
        description = snippet.description,
        videoOwnerChannelId = LiveChannel.Id(snippet.videoOwnerChannelId),
        publishedAt = snippet.publishedAt.toInstant(),
    ) {
        override fun toString(): String = toPrettyString()
    }
