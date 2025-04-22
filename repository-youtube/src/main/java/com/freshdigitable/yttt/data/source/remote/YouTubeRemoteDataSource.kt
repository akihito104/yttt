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

internal class YouTubeRemoteDataSource(
    private val youtube: YouTubeClient,
    private val ioScope: IoScope,
    private val dateTimeProvider: DateTimeProvider,
) : YouTubeDataSource.Remote {
    override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions.Paged>> =
        ioScope.asResultFlow {
            var paged = PagedSubscription()
            do {
                val res = youtube.fetchSubscription(pageSize, paged.itemSize, paged.nextPageToken)
                paged = paged.update(res.items, res.nextPageToken, dateTimeProvider.now())
                emit(Result.success(paged))
            } while (paged.nextPageToken != null)
        }.map { res -> res.recoverCatching { throw YouTubeException(it) } }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): Result<List<YouTubeChannelLog>> = fetchAllItems {
        fetchLiveChannelLogs(channelId, publishedAfter, maxResult, it)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<YouTubeVideo>> =
        fetchList(ids) { fetchVideoList(it) }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannelDetail>> =
        fetchList(ids) { fetchChannelList(it) }

    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> =
        fetch { fetchChannelSection(id).items }

    override suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<List<YouTubePlaylistItem>> = fetch { fetchPlaylistItems(id, maxResult).items }

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<YouTubePlaylist>> =
        fetchList(ids) { fetchPlaylist(it) }

    private suspend inline fun <E> fetchAllItems(
        crossinline request: YouTubeClient.(String?) -> YouTubeClient.Response<E>,
    ): Result<List<E>> = ioScope.asResult {
        buildList {
            var token: String? = null
            do {
                val response = youtube.request(token)
                addAll(response.items)
                token = response.nextPageToken
            } while (token != null)
        }
    }.recoverCatching { throw YouTubeException(it) }

    private suspend inline fun <I : IdBase, E> fetchList(
        ids: Set<I>,
        crossinline request: YouTubeClient.(Set<I>) -> YouTubeClient.Response<E>,
    ): Result<List<E>> = ioScope.asResult {
        if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size <= YouTubeDataSource.MAX_BATCH_SIZE) {
            youtube.request(ids).items
        } else {
            ids.chunked(YouTubeDataSource.MAX_BATCH_SIZE)
                .map { async { youtube.request(it.toSet()).items } }
                .awaitAll()
                .flatten()
        }
    }.recoverCatching { throw YouTubeException(it) }

    private suspend inline fun <T> fetch(crossinline request: YouTubeClient.() -> T): Result<T> =
        ioScope.asResult { youtube.request() }.recoverCatching { throw YouTubeException(it) }
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

private data class PagedSubscription(
    private val pages: List<List<YouTubeSubscription>> = emptyList(),
    val nextPageToken: String? = null,
    private val updatedAt: Instant? = null,
) : YouTubeSubscriptions.Paged {
    override val items: List<YouTubeSubscription> get() = pages.flatten()
    override val lastUpdatedAt: Instant get() = updatedAt ?: Instant.EPOCH
    override val lastPage: List<YouTubeSubscription> get() = pages.last()
    override val hasNextPage: Boolean get() = nextPageToken != null
    val itemSize: Int get() = pages.sumOf { it.size }
    fun update(
        items: List<YouTubeSubscription>,
        nextPageToken: String?,
        updatedAt: Instant? = null,
    ): PagedSubscription = copy(
        pages = pages + listOf(items),
        nextPageToken = nextPageToken,
        updatedAt = updatedAt,
    )
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

interface YouTubeClient {
    fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?,
    ): Response<YouTubeSubscription>

    fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Response<YouTubeChannelDetail>
    fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Response<YouTubePlaylist>
    fun fetchPlaylistItems(id: YouTubePlaylist.Id, maxResult: Long): Response<YouTubePlaylistItem>
    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Response<YouTubeVideo>
    fun fetchChannelSection(id: YouTubeChannel.Id): Response<YouTubeChannelSection>
    fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): Response<YouTubeChannelLog>

    class Response<T>(
        val items: List<T>,
        val nextPageToken: String? = null,
    )

    companion object {
        fun create(youtube: YouTube): YouTubeClient = YouTubeClientImpl(youtube)
    }
}

internal class YouTubeClientImpl(private val youtube: YouTube) : YouTubeClient {
    override fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?,
    ): YouTubeClient.Response<YouTubeSubscription> {
        val res = youtube.subscriptions()
            .list(listOf(PART_SNIPPET))
            .setMine(true)
            .setMaxResults(pageSize)
            .setPageToken(token)
            .execute()
        return YouTubeClient.Response(
            items = res.items.mapIndexed { i, s -> s.toLiveSubscription(offset + i) },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): YouTubeClient.Response<YouTubeChannelDetail> {
        val res = youtube.channels()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
            .setId(ids.map { it.value })
            .setMaxResults(ids.size.toLong())
            .execute()
        return YouTubeClient.Response(items = res.items.map { YouTubeChannelImpl(it) })
    }

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): YouTubeClient.Response<YouTubePlaylist> {
        val res = youtube.playlists()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setId(ids.map { it.value })
            .setMaxResults(ids.size.toLong())
            .execute()
        return YouTubeClient.Response(
            items = res.items.map { it.toLivePlaylist() },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): YouTubeClient.Response<YouTubePlaylistItem> {
        val res = youtube.playlistItems()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .execute()
        return YouTubeClient.Response(
            items = res.items.map { it.toLivePlaylistItem() },
        )
    }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): YouTubeClient.Response<YouTubeVideo> {
        val res = youtube.videos()
            .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
            .setId(ids.map { it.value })
            .setMaxResults(ids.size.toLong())
            .execute()
        return YouTubeClient.Response(
            items = res.items.map { YouTubeVideoRemote(it) },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchChannelSection(id: YouTubeChannel.Id): YouTubeClient.Response<YouTubeChannelSection> {
        val res = youtube.channelSections()
            .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
            .setChannelId(id.value)
            .execute()
        return YouTubeClient.Response(
            items = res.items.map { YouTubeChannelSectionImpl(it) },
        )
    }

    override fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): YouTubeClient.Response<YouTubeChannelLog> {
        check(publishedAfter != null || maxResult != null) { "publishedAfter or maxResult should not be null" }
        val res = youtube.activities()
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
            .execute()
        return YouTubeClient.Response(
            items = res.items.map { YouTubeChannelLogEntity(it) },
            nextPageToken = res.nextPageToken,
        )
    }

    companion object {
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
    }
}
