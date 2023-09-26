package com.freshdigitable.yttt.data.source.remote

import android.util.Log
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelLogEntity
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LivePlaylistItemEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.ChannelSection
import com.google.api.services.youtube.model.ChannelSectionSnippet
import com.google.api.services.youtube.model.Playlist
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveRemoteDataSource @Inject constructor(
    httpRequestInitializer: HttpRequestInitializer,
    private val coroutineScope: CoroutineScope,
) : YoutubeLiveDataSource {
    private val youtube = YouTube.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(), httpRequestInitializer,
    ).build()

    override suspend fun fetchAllSubscribe(
        maxResult: Long,
    ): List<LiveSubscription> = fetchAllItems(
        requestParams = { token ->
            subscriptions()
                .list(listOf(PART_SNIPPET))
                .setMine(true)
                .setMaxResults(maxResult)
                .setPageToken(token)
        },
        getItems = { items },
        getNextToken = { nextPageToken },
    ).mapIndexed { i, s -> s.toLiveSubscription(i) }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<LiveChannelLog> = fetchAllItems(
        requestParams = { token ->
            activities()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setChannelId(channelId.value).apply {
                    if (maxResult != null) {
                        this.maxResults = maxResult
                    }
                    if (publishedAfter != null) {
                        setPublishedAfter(publishedAfter.toString())
                    }
                }
                .setPageToken(token)
        },
        getItems = { items },
        getNextToken = { nextPageToken },
    ).filter { it.contentDetails?.upload != null }
        .map { it.toChannelLog() }

    override suspend fun fetchVideoList(
        ids: Collection<LiveVideo.Id>,
    ): List<LiveVideoDetail> = fetchList(ids, getItems = { items }) { chunked ->
        videos()
            .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
            .setId(chunked.map { it.value })
            .setMaxResults(VIDEO_MAX_FETCH_SIZE.toLong())
    }.map { it.toLiveVideo() }

    suspend fun fetchChannelList(
        ids: Collection<LiveChannel.Id>,
    ): List<LiveChannelDetail> = fetchList(ids, getItems = { items }) { chunked ->
        channels()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
            .setId(chunked.map { it.value })
            .setMaxResults(VIDEO_MAX_FETCH_SIZE.toLong())
    }.map { LiveChannelImpl(it) }

    suspend fun fetchChannelSection(
        channelId: LiveChannel.Id,
    ): List<LiveChannelSection> = fetch {
        channelSections()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setChannelId(channelId.value)
    }.items.map { LiveChannelSectionImpl(it) }

    suspend fun fetchPlaylistItems(
        id: LivePlaylist.Id,
        maxResult: Long = 20,
        pageToken: String? = null,
    ): List<LivePlaylistItem> = fetch {
        playlistItems()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .setPageToken(pageToken)
    }.items.map { it.toLivePlaylistItem() }

    suspend fun fetchPlaylist(
        ids: Collection<LivePlaylist.Id>,
    ): List<LivePlaylist> = fetchList(ids, getItems = { items }) { chunked ->
        playlists()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setId(chunked.map { it.value })
            .setMaxResults(VIDEO_MAX_FETCH_SIZE.toLong())
    }.map { it.toLivePlaylist() }

    private suspend fun <T, E> fetchAllItems(
        requestParams: YouTube.(String?) -> AbstractGoogleClientRequest<T>,
        getItems: T.() -> List<E>,
        getNextToken: T.() -> String?,
    ): List<E> {
        var token: String? = null
        val res = mutableListOf<E>()
        do {
            val response = fetch { requestParams(token) }
            res.addAll(response.getItems())
            token = response.getNextToken()
        } while (token != null)
        return res
    }

    private suspend fun <ID, T, E> fetchList(
        ids: Collection<IdBase<ID>>,
        getItems: T.() -> List<E>,
        requestParams: YouTube.(Collection<IdBase<ID>>) -> AbstractGoogleClientRequest<T>,
    ): List<E> {
        if (ids.size <= VIDEO_MAX_FETCH_SIZE) {
            return fetch { requestParams(ids) }.getItems()
        }
        return ids.chunked(VIDEO_MAX_FETCH_SIZE)
            .map { chunked -> coroutineScope.async { fetch { requestParams(chunked) }.getItems() } }
            .awaitAll()
            .flatten()
    }

    private suspend fun <T> fetch(requestParams: YouTube.() -> AbstractGoogleClientRequest<T>): T {
        val params = requestParams(youtube)
        return withContext(Dispatchers.IO) { params.execute() }
    }

    override suspend fun addFreeChatItems(ids: Collection<LiveVideo.Id>) =
        throw UnsupportedOperationException()

    override suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>) =
        throw UnsupportedOperationException()

    companion object {
        @Suppress("unused")
        private val TAG = YouTubeLiveRemoteDataSource::class.simpleName
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"

        // https://developers.google.com/youtube/v3/docs/videos/list#parameters
        private const val VIDEO_MAX_FETCH_SIZE = 50
    }
}

internal class HttpRequestInitializerImpl(
    private val credential: GoogleAccountCredential,
    private val dataStore: AccountLocalDataSource,
) : HttpRequestInitializer {
    override fun initialize(request: HttpRequest?) {
        Log.d("YouTubeLiveRemoteDataSource", "init: ${request?.url}")
        val account = checkNotNull(dataStore.getAccount()) { "google account is null." }
        credential.selectedAccountName = account
        credential.initialize(request)
    }
}

private fun liveChannelId(id: String): LiveChannel.Id = LiveChannel.Id(id, LivePlatform.YOUTUBE)
private fun liveVideoId(id: String): LiveVideo.Id = LiveVideo.Id(id, LivePlatform.YOUTUBE)
private fun Subscription.toLiveSubscription(order: Int): LiveSubscription = LiveSubscriptionEntity(
    id = LiveSubscription.Id(id, LivePlatform.YOUTUBE),
    subscribeSince = Instant.ofEpochMilli(snippet.publishedAt.value),
    channel = LiveChannelEntity(
        id = liveChannelId(snippet.resourceId.channelId),
        iconUrl = snippet.thumbnails.url,
        title = snippet.title,
    ),
    order = order,
)

private fun Activity.toChannelLog(): LiveChannelLog = LiveChannelLogEntity(
    id = LiveChannelLog.Id(id),
    dateTime = Instant.ofEpochMilli(snippet.publishedAt.value),
    videoId = liveVideoId(contentDetails.upload.videoId),
    channelId = liveChannelId(snippet.channelId),
    thumbnailUrl = snippet.thumbnails.url,
)

private fun Video.toLiveVideo(): LiveVideoDetail =
    object : LiveVideoDetail, LiveVideo by LiveVideoEntity(
        id = liveVideoId(id),
        channel = LiveChannelEntity(
            id = liveChannelId(snippet.channelId),
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
    get() = (maxres ?: high ?: standard ?: medium ?: default)?.url ?: ""

private data class LiveChannelImpl(
    private val channel: Channel,
) : LiveChannelDetail, LiveChannel by LiveChannelEntity(
    id = liveChannelId(channel.id),
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
    override val id: LiveChannelSection.Id
        get() = LiveChannelSection.Id(channelSection.id)
    override val channelId: LiveChannel.Id
        get() = liveChannelId(channelSection.snippet.channelId)
    override val title: String?
        get() = channelSection.snippet.title
    override val position: Long
        get() = channelSection.snippet.position
    override val type: LiveChannelSection.Type
        get() = requireNotNull(channelSection.snippet.parseType()) { channelSection.snippet }
    override val content: LiveChannelSection.Content<*>?
        get() = channelSection.parseContent()

    override fun toString(): String = channelSection.toPrettyString()

    companion object {
        private val typeTable = mapOf(
            "allPlaylists" to LiveChannelSection.Type.ALL_PLAYLIST,
            "completedEvents" to LiveChannelSection.Type.COMPLETED_EVENT,
            "liveEvents" to LiveChannelSection.Type.LIVE_EVENT,
            "multipleChannels" to LiveChannelSection.Type.MULTIPLE_CHANNEL,
            "multiplePlaylists" to LiveChannelSection.Type.MULTIPLE_PLAYLIST,
            "popularUploads" to LiveChannelSection.Type.POPULAR_UPLOAD,
            "recentUploads" to LiveChannelSection.Type.RECENT_UPLOAD,
            "singlePlaylist" to LiveChannelSection.Type.SINGLE_PLAYLIST,
            "subscriptions" to LiveChannelSection.Type.SUBSCRIPTION,
            "upcomingEvents" to LiveChannelSection.Type.UPCOMING_EVENT,
            "channelsectiontypeundefined" to LiveChannelSection.Type.UNDEFINED,
        )

        private fun ChannelSectionSnippet.parseType(): LiveChannelSection.Type? {
            return typeTable[type]
                ?: typeTable.entries.firstOrNull { it.key.lowercase() == type.lowercase() }?.value
        }

        private fun ChannelSection.parseContent(): LiveChannelSection.Content<*>? {
            if (contentDetails == null) {
                return null
            }
            return when (requireNotNull(snippet.parseType()).metaType) {
                LiveChannelSection.Content.Playlist::class -> LiveChannelSection.Content.Playlist(
                    contentDetails.playlists.map { LivePlaylist.Id(it) }
                )

                LiveChannelSection.Content.Channels::class -> LiveChannelSection.Content.Channels(
                    contentDetails.channels.map { liveChannelId(it) }
                )

                else -> throw IllegalStateException()
            }
        }
    }
}

private fun Playlist.toLivePlaylist() = object : LivePlaylist {
    override val id: LivePlaylist.Id
        get() = LivePlaylist.Id(this@toLivePlaylist.id)
    override val title: String
        get() = this@toLivePlaylist.snippet.title
    override val thumbnailUrl: String
        get() = this@toLivePlaylist.snippet.thumbnails.url

    override fun toString(): String = this@toLivePlaylist.toPrettyString()
}

private fun PlaylistItem.toLivePlaylistItem(): LivePlaylistItem =
    object : LivePlaylistItem by LivePlaylistItemEntity(
        id = LivePlaylistItem.Id(id),
        playlistId = LivePlaylist.Id(snippet.playlistId),
        title = snippet.title,
        thumbnailUrl = snippet.thumbnails?.url ?: "",
        videoId = liveVideoId(contentDetails.videoId),
        channel = LiveChannelEntity(
            id = liveChannelId(snippet.channelId),
            title = snippet.channelTitle,
            iconUrl = "",
        ),
        description = snippet.description,
        videoOwnerChannelId = snippet.videoOwnerChannelId?.let { liveChannelId(it) },
        publishedAt = snippet.publishedAt.toInstant(),
    ) {
        override fun toString(): String = toPrettyString()
    }
