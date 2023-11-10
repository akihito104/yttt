package com.freshdigitable.yttt.data.source.remote

import android.util.Log
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelLogEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEntity
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionEntity
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoDetail
import com.freshdigitable.yttt.data.model.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
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
class YouTubeRemoteDataSource @Inject constructor(
    httpRequestInitializer: HttpRequestInitializer,
    private val coroutineScope: CoroutineScope,
) : YoutubeDataSource {
    private val youtube = YouTube.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(), httpRequestInitializer,
    ).build()

    override suspend fun fetchAllSubscribe(
        maxResult: Long,
    ): List<YouTubeSubscription> = fetchAllItems(
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
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?
    ): List<YouTubeChannelLog> = fetchAllItems(
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

    override suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideoDetail> =
        fetchList(ids, getItems = { items }) { chunked ->
            videos()
                .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(chunked.map { it.value })
                .setMaxResults(VIDEO_MAX_FETCH_SIZE.toLong())
        }.map { it.toLiveVideo() }

    suspend fun fetchChannelList(
        ids: Collection<YouTubeChannel.Id>,
    ): List<YouTubeChannelDetail> = fetchList(ids, getItems = { items }) { chunked ->
        channels()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
            .setId(chunked.map { it.value })
            .setMaxResults(VIDEO_MAX_FETCH_SIZE.toLong())
    }.map { YouTubeChannelImpl(it) }

    suspend fun fetchChannelSection(
        channelId: YouTubeChannel.Id,
    ): List<YouTubeChannelSection> = fetch {
        channelSections()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setChannelId(channelId.value)
    }.items.map { YouTubeChannelSectionImpl(it) }

    suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long = 20,
        pageToken: String? = null,
    ): List<YouTubePlaylistItem> = fetch {
        playlistItems()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .setPageToken(pageToken)
    }.items.map { it.toLivePlaylistItem() }

    suspend fun fetchPlaylist(
        ids: Collection<YouTubePlaylist.Id>,
    ): List<YouTubePlaylist> = fetchList(ids, getItems = { items }) { chunked ->
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
        if (ids.isEmpty()) {
            return emptyList()
        }
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

    override suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>) =
        throw UnsupportedOperationException()

    override suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>) =
        throw UnsupportedOperationException()

    companion object {
        @Suppress("unused")
        private val TAG = YouTubeRemoteDataSource::class.simpleName
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

private fun Subscription.toLiveSubscription(order: Int): YouTubeSubscription =
    YouTubeSubscriptionEntity(
        id = YouTubeSubscription.Id(id),
        subscribeSince = Instant.ofEpochMilli(snippet.publishedAt.value),
        channel = YouTubeChannelEntity(
            id = YouTubeChannel.Id(snippet.resourceId.channelId),
            iconUrl = snippet.thumbnails.url,
            title = snippet.title,
        ),
        order = order,
    )

private fun Activity.toChannelLog(): YouTubeChannelLog = YouTubeChannelLogEntity(
    id = YouTubeChannelLog.Id(id),
    dateTime = Instant.ofEpochMilli(snippet.publishedAt.value),
    videoId = YouTubeVideo.Id(contentDetails.upload.videoId),
    channelId = YouTubeChannel.Id(snippet.channelId),
    thumbnailUrl = snippet.thumbnails.url,
)

private fun Video.toLiveVideo(): YouTubeVideoDetail =
    object : YouTubeVideoDetail, YouTubeVideo by YouTubeVideoEntity(
        id = YouTubeVideo.Id(id),
        channel = YouTubeChannelEntity(
            id = YouTubeChannel.Id(snippet.channelId),
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

private data class YouTubeChannelImpl(
    private val channel: Channel,
) : YouTubeChannelDetail, YouTubeChannel by YouTubeChannelEntity(
    id = YouTubeChannel.Id(channel.id),
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
    override val uploadedPlayList: YouTubePlaylist.Id?
        get() = channel.contentDetails?.relatedPlaylists?.uploads?.let { YouTubePlaylist.Id(it) }

    override fun toString(): String = channel.toPrettyString()
}

private data class YouTubeChannelSectionImpl(
    private val channelSection: ChannelSection
) : YouTubeChannelSection {
    override val id: YouTubeChannelSection.Id
        get() = YouTubeChannelSection.Id(channelSection.id)
    override val channelId: YouTubeChannel.Id
        get() = YouTubeChannel.Id(channelSection.snippet.channelId)
    override val title: String?
        get() = channelSection.snippet.title
    override val position: Long
        get() = channelSection.snippet.position
    override val type: YouTubeChannelSection.Type
        get() = requireNotNull(channelSection.snippet.parseType()) { channelSection.snippet }
    override val content: YouTubeChannelSection.Content<*>?
        get() = channelSection.parseContent()

    override fun toString(): String = channelSection.toPrettyString()

    companion object {
        private val typeTable = mapOf(
            "allPlaylists" to YouTubeChannelSection.Type.ALL_PLAYLIST,
            "completedEvents" to YouTubeChannelSection.Type.COMPLETED_EVENT,
            "liveEvents" to YouTubeChannelSection.Type.LIVE_EVENT,
            "multipleChannels" to YouTubeChannelSection.Type.MULTIPLE_CHANNEL,
            "multiplePlaylists" to YouTubeChannelSection.Type.MULTIPLE_PLAYLIST,
            "popularUploads" to YouTubeChannelSection.Type.POPULAR_UPLOAD,
            "recentUploads" to YouTubeChannelSection.Type.RECENT_UPLOAD,
            "singlePlaylist" to YouTubeChannelSection.Type.SINGLE_PLAYLIST,
            "subscriptions" to YouTubeChannelSection.Type.SUBSCRIPTION,
            "upcomingEvents" to YouTubeChannelSection.Type.UPCOMING_EVENT,
            "channelsectiontypeundefined" to YouTubeChannelSection.Type.UNDEFINED,
        )

        private fun ChannelSectionSnippet.parseType(): YouTubeChannelSection.Type? {
            return typeTable[type]
                ?: typeTable.entries.firstOrNull { it.key.lowercase() == type.lowercase() }?.value
        }

        private fun ChannelSection.parseContent(): YouTubeChannelSection.Content<*>? {
            if (contentDetails == null) {
                return null
            }
            return when (requireNotNull(snippet.parseType()).metaType) {
                YouTubeChannelSection.Content.Playlist::class -> YouTubeChannelSection.Content.Playlist(
                    contentDetails.playlists.map { YouTubePlaylist.Id(it) }
                )

                YouTubeChannelSection.Content.Channels::class -> YouTubeChannelSection.Content.Channels(
                    contentDetails.channels.map { YouTubeChannel.Id(it) }
                )

                else -> throw IllegalStateException()
            }
        }
    }
}

private fun Playlist.toLivePlaylist() = object : YouTubePlaylist {
    override val id: YouTubePlaylist.Id
        get() = YouTubePlaylist.Id(this@toLivePlaylist.id)
    override val title: String
        get() = this@toLivePlaylist.snippet.title
    override val thumbnailUrl: String
        get() = this@toLivePlaylist.snippet.thumbnails.url

    override fun toString(): String = this@toLivePlaylist.toPrettyString()
}

private fun PlaylistItem.toLivePlaylistItem(): YouTubePlaylistItem =
    object : YouTubePlaylistItem by YouTubePlaylistItemEntity(
        id = YouTubePlaylistItem.Id(id),
        playlistId = YouTubePlaylist.Id(snippet.playlistId),
        title = snippet.title,
        thumbnailUrl = snippet.thumbnails?.url ?: "",
        videoId = YouTubeVideo.Id(contentDetails.videoId),
        channel = YouTubeChannelEntity(
            id = YouTubeChannel.Id(snippet.channelId),
            title = snippet.channelTitle,
            iconUrl = "",
        ),
        description = snippet.description,
        videoOwnerChannelId = snippet.videoOwnerChannelId?.let { YouTubeChannel.Id(it) },
        publishedAt = snippet.publishedAt.toInstant(),
    ) {
        override fun toString(): String = toPrettyString()
    }
