package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEntity
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpResponseException
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
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeRemoteDataSource @Inject constructor(
    private val youtube: YouTube,
    private val ioScope: IoScope,
    private val dateTimeProvider: DateTimeProvider,
) : YouTubeDataSource.Remote {
    override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions.Paged>> =
        ioScope.asResultFlow {
            val paged = PagedSubscription()
            do {
                val res = youtube.subscriptions()
                    .list(listOf(PART_SNIPPET))
                    .setMine(true)
                    .setMaxResults(pageSize)
                    .setPageToken(paged.nextPageToken)
                    .execute()
                val offset = paged.itemSize
                val subs = res.items.mapIndexed { i, s -> s.toLiveSubscription(offset + i) }
                val value = paged.update(subs, res.nextPageToken, dateTimeProvider.now())
                emit(Result.success(value))
            } while (res.nextPageToken != null)
        }.map { res -> res.recoverCatching { throw YouTubeException(it) } }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?
    ): Result<List<YouTubeChannelLog>> = fetchAllItems(
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
    ).map { res -> res.map { YouTubeChannelLogEntity(activity = it) } }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideo>> =
        fetchList(ids, getItems = { items }) { chunked ->
            videos()
                .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(chunked.map { it.value })
                .setMaxResults(chunked.size.toLong())
        }.map { v -> v.map { YouTubeVideoRemote(it) } }

    override suspend fun fetchChannelList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<YouTubeChannelDetail>> = fetchList(ids, getItems = { items }) { chunked ->
        channels()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
            .setId(chunked.map { it.value })
            .setMaxResults(chunked.size.toLong())
    }.map { c -> c.map { YouTubeChannelImpl(it) } }

    override suspend fun fetchChannelSection(
        id: YouTubeChannel.Id,
    ): Result<List<YouTubeChannelSection>> = fetch {
        channelSections()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setChannelId(id.value)
    }.map { res -> res.items.map { YouTubeChannelSectionImpl(it) } }

    override suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<List<YouTubePlaylistItem>> = fetch {
        playlistItems()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
    }.map { res -> res.items.map { it.toLivePlaylistItem() } }

    override suspend fun fetchPlaylist(
        ids: Set<YouTubePlaylist.Id>,
    ): Result<List<YouTubePlaylist>> = fetchList(ids, getItems = { items }) { chunked ->
        playlists()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setId(chunked.map { it.value })
            .setMaxResults(chunked.size.toLong())
    }.map { res -> res.map { it.toLivePlaylist() } }

    private suspend inline fun <T, E> fetchAllItems(
        crossinline requestParams: YouTube.(String?) -> AbstractGoogleClientRequest<T>,
        crossinline getItems: T.() -> List<E>,
        crossinline getNextToken: T.() -> String?,
    ): Result<List<E>> = ioScope.asResult {
        buildList {
            var token: String? = null
            do {
                val response = youtube.requestParams(token).execute()
                addAll(response.getItems())
                token = response.getNextToken()
            } while (token != null)
        }
    }.recoverCatching { throw YouTubeException(it) }

    private suspend inline fun <T, E> fetchList(
        ids: Set<IdBase>,
        crossinline getItems: T.() -> List<E>,
        crossinline requestParams: YouTube.(Set<IdBase>) -> AbstractGoogleClientRequest<T>,
    ): Result<List<E>> = ioScope.asResult {
        if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size <= YouTubeDataSource.MAX_BATCH_SIZE) {
            youtube.requestParams(ids).execute().getItems()
        } else {
            ids.chunked(YouTubeDataSource.MAX_BATCH_SIZE)
                .map { async { youtube.requestParams(it.toSet()).execute().getItems() } }
                .awaitAll()
                .flatten()
        }
    }.recoverCatching { throw YouTubeException(it) }

    private suspend inline fun <T> fetch(
        crossinline requestParams: YouTube.() -> AbstractGoogleClientRequest<T>,
    ): Result<T> = ioScope.asResult { youtube.requestParams().execute() }
        .recoverCatching { throw YouTubeException(it) }

    companion object {
        @Suppress("unused")
        private val TAG = YouTubeRemoteDataSource::class.simpleName
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
    }
}

internal class YouTubeException(
    private val throwable: Throwable,
) : IoScope.NetworkException(throwable) {
    override val statusCode: Int
        get() = when (throwable) {
            is HttpResponseException -> throwable.statusCode
            else -> -1
        }
}

private class PagedSubscription : YouTubeSubscriptions.Paged {
    private val pages = mutableListOf<List<YouTubeSubscription>>()
    var nextPageToken: String? = null
    private var updatedAt: Instant? = null
    override val items: List<YouTubeSubscription> get() = pages.flatten()
    override val lastUpdatedAt: Instant get() = updatedAt ?: Instant.EPOCH
    override val lastPage: List<YouTubeSubscription> get() = pages.last()
    override val hasNextPage: Boolean get() = nextPageToken != null
    val itemSize: Int get() = pages.sumOf { it.size }
    fun update(
        items: List<YouTubeSubscription>,
        nextPageToken: String?,
        updatedAt: Instant? = null,
    ): PagedSubscription {
        pages.add(items)
        this.nextPageToken = nextPageToken
        this.updatedAt = if (hasNextPage) null else updatedAt
        return this
    }
}

private fun Subscription.toLiveSubscription(order: Int): YouTubeSubscription =
    YouTubeSubscriptionRemote(this, order)

private data class YouTubeSubscriptionRemote(
    private val subscription: Subscription,
    override val order: Int,
) : YouTubeSubscription {
    override val id: YouTubeSubscription.Id
        get() = YouTubeSubscription.Id(subscription.id)
    override val subscribeSince: Instant
        get() = Instant.ofEpochMilli(subscription.snippet.publishedAt.value)
    override val channel: YouTubeChannel
        get() = YouTubeChannelEntity(
            id = YouTubeChannel.Id(subscription.snippet.resourceId.channelId),
            iconUrl = subscription.snippet.thumbnails.iconUrl,
            title = subscription.snippet.title,
        )
}

private data class YouTubeChannelLogEntity(
    val activity: Activity,
) : YouTubeChannelLog {
    override val id: YouTubeChannelLog.Id
        get() = YouTubeChannelLog.Id(activity.id)
    override val dateTime: Instant
        get() = Instant.ofEpochMilli(activity.snippet.publishedAt.value)
    override val videoId: YouTubeVideo.Id?
        get() = activity.contentDetails.upload?.let { YouTubeVideo.Id(it.videoId) }
    override val channelId: YouTubeChannel.Id
        get() = YouTubeChannel.Id(activity.snippet.channelId)
    override val thumbnailUrl: String
        get() = activity.snippet.thumbnails.url
    override val title: String
        get() = activity.snippet.title
    override val type: String
        get() = activity.snippet.type
}

private class YouTubeVideoRemote(
    private val video: Video,
) : YouTubeVideo {
    private val liveStreamingDetails: VideoLiveStreamingDetails? get() = video.liveStreamingDetails
    private val snippet get() = requireNotNull(video.snippet) { "json: $video" }
    override val id: YouTubeVideo.Id = YouTubeVideo.Id(video.id)
    override val channel: YouTubeChannel = YouTubeChannelEntity(
        id = YouTubeChannel.Id(snippet.channelId),
        title = snippet.channelTitle,
        iconUrl = "",
    )
    override val title: String get() = snippet.title
    override val scheduledStartDateTime: Instant? =
        liveStreamingDetails?.scheduledStartTime?.toInstant()
    override val scheduledEndDateTime: Instant? =
        liveStreamingDetails?.scheduledEndTime?.toInstant()
    override val actualStartDateTime: Instant? = liveStreamingDetails?.actualStartTime?.toInstant()
    override val actualEndDateTime: Instant? = liveStreamingDetails?.actualEndTime?.toInstant()
    override val thumbnailUrl: String get() = snippet.thumbnails.url
    override val description: String get() = snippet.description
    override val viewerCount: BigInteger? get() = liveStreamingDetails?.concurrentViewers
    override val liveBroadcastContent: YouTubeVideo.BroadcastType =
        findBy(snippet.liveBroadcastContent)

    override fun toString(): String = video.toString()

    companion object {
        private fun findBy(name: String?): YouTubeVideo.BroadcastType = when (name) {
            "live" -> YouTubeVideo.BroadcastType.LIVE
            "upcoming" -> YouTubeVideo.BroadcastType.UPCOMING
            "none" -> YouTubeVideo.BroadcastType.NONE
            null -> YouTubeVideo.BroadcastType.NONE
            else -> throw NotImplementedError("unknown liveBroadcastContent: $name")
        }
    }
}

private fun DateTime.toInstant(): Instant = Instant.ofEpochMilli(value)

/**
 * maxres: 720p, standard: 640x480, high: 480x360, medium: 240x180, default: 120x90.
 *
 * if original thumbnail size is `maxres` (16:9), sizes below `standard` (4:3) will have black bands at top and bottom.
 */
private val ThumbnailDetails.url: String
    get() = (/*maxres ?:*/ standard ?: high ?: medium ?: default)?.url ?: ""

/**
 * high: 800px, medium: 240px, default: 88px
 */
private val ThumbnailDetails.iconUrl: String
    get() = (medium ?: high ?: default)?.url ?: ""

private data class YouTubeChannelImpl(
    private val channel: Channel,
) : YouTubeChannelDetail, YouTubeChannel by YouTubeChannelEntity(
    id = YouTubeChannel.Id(channel.id),
    title = channel.snippet.title,
    iconUrl = channel.snippet.thumbnails.iconUrl,
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
        get() = channel.snippet.customUrl ?: ""
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
        ).mapKeys { it.key.lowercase() }

        private fun ChannelSectionSnippet.parseType(): YouTubeChannelSection.Type? =
            typeTable[type.lowercase()]

        private fun ChannelSection.parseContent(): YouTubeChannelSection.Content<*>? {
            if (contentDetails == null) {
                return null
            }
            val type = requireNotNull(snippet.parseType()) { "type is null: $snippet" }
            return when (type) {
                YouTubeChannelSection.Type.SINGLE_PLAYLIST,
                YouTubeChannelSection.Type.MULTIPLE_PLAYLIST -> YouTubeChannelSection.Content.Playlist(
                    contentDetails.playlists.map { YouTubePlaylist.Id(it) }
                )

                YouTubeChannelSection.Type.MULTIPLE_CHANNEL -> YouTubeChannelSection.Content.Channels(
                    contentDetails.channels.map { YouTubeChannel.Id(it) }
                )

                else -> throw IllegalStateException("unknown type: $snippet")
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
