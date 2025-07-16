package com.freshdigitable.yttt.data.source.remote

import androidx.annotation.VisibleForTesting
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.ActivityListResponse
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.ChannelListResponse
import com.google.api.services.youtube.model.ChannelSection
import com.google.api.services.youtube.model.ChannelSectionListResponse
import com.google.api.services.youtube.model.ChannelSectionSnippet
import com.google.api.services.youtube.model.Playlist
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.PlaylistItemListResponse
import com.google.api.services.youtube.model.PlaylistListResponse
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.SubscriptionListResponse
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoListResponse
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

interface YouTubeClient {
    fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?,
    ): NetworkResponse<List<YouTubeSubscription>>

    fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>>
    fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>>
    fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String? = null,
    ): NetworkResponse<List<YouTubePlaylistItem>>

    fun fetchPlaylistItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItemDetail>>

    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>>
    fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>>
    fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): NetworkResponse<List<YouTubeChannelLog>>

    companion object {
        fun create(youtube: YouTube): YouTubeClient = YouTubeClientImpl(youtube)

        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(5)
    }
}

internal class YouTubeClientImpl(
    private val youtube: YouTube,
) : YouTubeClient {
    override fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?,
    ): NetworkResponse<List<YouTubeSubscription>> =
        youtube.fetch(YouTubeSubscriptionRemote.factory(offset)) {
            subscriptions()
                .list(listOf(PART_SNIPPET))
                .setMine(true)
                .setMaxResults(pageSize)
                .setPageToken(token)
        }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
        youtube.fetch(YouTubeChannelImpl.factory) {
            channels()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> =
        youtube.fetch(YouTubePlaylistRemote.factory) {
            playlists()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?
    ): NetworkResponse<List<YouTubePlaylistItem>> = youtube.fetch(PlaylistItemRemote.factory(id)) {
        playlistItems()
            .list(listOf(PART_CONTENT_DETAILS))
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .apply { eTag?.let { requestHeaders = HttpHeaders().apply { setIfNoneMatch(it) } } }
    }

    override fun fetchPlaylistItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItemDetail>> =
        youtube.fetch(PlaylistItemDetailRemote.factory) {
            playlistItems()
                .list(listOf(PART_SNIPPET))
                .setPlaylistId(id.value)
                .setMaxResults(maxResult)
        }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> =
        youtube.fetch(YouTubeVideoRemote.factory) {
            videos()
                .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }

    override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> =
        youtube.fetch(YouTubeChannelSectionImpl.factory) {
            channelSections()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setChannelId(id.value)
        }

    override fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): NetworkResponse<List<YouTubeChannelLog>> {
        check(publishedAfter != null || maxResult != null) { "publishedAfter or maxResult should not be null" }
        return youtube.fetch(YouTubeChannelLogEntity.factory) {
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
        }
    }

    companion object {
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
        private fun <R, T> YouTube.fetch(
            factory: ResponseFactory<R, T>,
            request: YouTube.() -> AbstractGoogleClientRequest<R>,
        ): NetworkResponse<T> = try {
            val req = request()
            val res = req.executeUnparsed()
            val date = DateTimeFormatter.RFC_1123_DATE_TIME.parse(res.headers.date)
            val parseAs = res.parseAs(req.responseClass)
            factory(parseAs, CacheControl.create(Instant.from(date), MAX_AGE_DEFAULT))
        } catch (e: HttpResponseException) {
            val date = DateTimeFormatter.RFC_1123_DATE_TIME.parse(e.headers.date)
            val cacheControl = CacheControl.create(Instant.from(date), MAX_AGE_DEFAULT)
            throw YouTubeException(e.statusCode, e.statusMessage, e, cacheControl)
        }
    }
}

class YouTubeException(
    override val statusCode: Int,
    private val statusMessage: String,
    throwable: Throwable? = null,
    override val cacheControl: CacheControl,
) : NetworkResponse.Exception(throwable) {
    override val isQuotaExceeded: Boolean
        get() = statusCode == 403 && statusMessage == "quotaExceeded"

    companion object
}

typealias ResponseFactory<R, T> = (R, CacheControl) -> NetworkResponse<T>

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

    companion object {
        fun factory(orderOffset: Int): ResponseFactory<SubscriptionListResponse, List<YouTubeSubscription>> =
            { res, cacheControl ->
                NetworkResponse.create(
                    item = res.items.mapIndexed { i, s ->
                        YouTubeSubscriptionRemote(s, orderOffset + i)
                    },
                    cacheControl = cacheControl,
                    nextPageToken = res.nextPageToken,
                )
            }
    }
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

    companion object {
        val factory: ResponseFactory<ActivityListResponse, List<YouTubeChannelLog>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeChannelLogEntity(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
    }
}

@VisibleForTesting
internal class YouTubeVideoRemote(
    private val video: Video,
) : YouTubeVideo {
    private val liveStreamingDetails: VideoLiveStreamingDetails? get() = video.liveStreamingDetails
    private val snippet get() = requireNotNull(video.snippet) { "json: $video" }
    override val id: YouTubeVideo.Id get() = YouTubeVideo.Id(video.id)
    override val channel: YouTubeChannelTitle
        get() = YouTubeChannelTitleImpl(
            id = YouTubeChannel.Id(snippet.channelId),
            title = snippet.channelTitle,
        )
    override val title: String get() = snippet.title
    override val scheduledStartDateTime: Instant?
        get() = liveStreamingDetails?.scheduledStartTime?.toInstant()
    override val scheduledEndDateTime: Instant?
        get() = liveStreamingDetails?.scheduledEndTime?.toInstant()
    override val actualStartDateTime: Instant? get() = liveStreamingDetails?.actualStartTime?.toInstant()
    override val actualEndDateTime: Instant? get() = liveStreamingDetails?.actualEndTime?.toInstant()
    override val thumbnailUrl: String get() = snippet.thumbnails.url
    override val description: String get() = snippet.description
    override val viewerCount: BigInteger? get() = liveStreamingDetails?.concurrentViewers
    override val liveBroadcastContent: YouTubeVideo.BroadcastType
        get() = findBy(snippet.liveBroadcastContent)

    override fun toString(): String = video.toString()

    companion object {
        val factory: ResponseFactory<VideoListResponse, List<YouTubeVideo>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeVideoRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }

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
) : YouTubeChannelDetail {
    override val id: YouTubeChannel.Id
        get() = YouTubeChannel.Id(channel.id)
    override val title: String
        get() = channel.snippet.title
    override val iconUrl: String
        get() = channel.snippet.thumbnails.iconUrl
    override val bannerUrl: String?
        get() = channel.brandingSettings?.image?.bannerExternalUrl
    override val subscriberCount: BigInteger
        get() = channel.statistics.subscriberCount
    override val isSubscriberHidden: Boolean
        get() = channel.statistics.hiddenSubscriberCount
    override val videoCount: BigInteger
        get() = channel.statistics.videoCount
    override val viewsCount: BigInteger
        get() = channel.statistics.viewCount ?: BigInteger.ZERO
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

    companion object {
        val factory: ResponseFactory<ChannelListResponse, List<YouTubeChannelDetail>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeChannelImpl(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
    }
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
        val factory: ResponseFactory<ChannelSectionListResponse, List<YouTubeChannelSection>> =
            { res, cc ->
                NetworkResponse.create(
                    item = res.items.map { YouTubeChannelSectionImpl(it) },
                    cacheControl = cc,
                )
            }

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

private class YouTubePlaylistRemote(
    private val playlist: Playlist,
) : YouTubePlaylist {
    override val id: YouTubePlaylist.Id get() = YouTubePlaylist.Id(playlist.id)
    override val title: String get() = playlist.snippet.title
    override val thumbnailUrl: String get() = playlist.snippet.thumbnails.url
    override fun toString(): String = playlist.toPrettyString()

    companion object {
        val factory: ResponseFactory<PlaylistListResponse, List<YouTubePlaylist>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubePlaylistRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
    }
}

private class PlaylistItemRemote(
    private val item: PlaylistItem,
    override val playlistId: YouTubePlaylist.Id,
) : YouTubePlaylistItem {
    override val id: YouTubePlaylistItem.Id get() = YouTubePlaylistItem.Id(item.id)
    override val videoId: YouTubeVideo.Id get() = YouTubeVideo.Id(item.contentDetails.videoId)
    override val publishedAt: Instant get() = item.contentDetails.videoPublishedAt.toInstant()

    companion object {
        val factory: (YouTubePlaylist.Id) -> ResponseFactory<PlaylistItemListResponse, List<YouTubePlaylistItem>> =
            { id ->
                { res, cc ->
                    NetworkResponse.create(
                        item = res.items.map { PlaylistItemRemote(item = it, playlistId = id) },
                        cacheControl = cc,
                        nextPageToken = res.nextPageToken,
                        eTag = res.etag,
                    )
                }
            }
    }
}

private class PlaylistItemDetailRemote(
    private val item: PlaylistItem,
) : YouTubePlaylistItemDetail {
    override val id: YouTubePlaylistItem.Id get() = YouTubePlaylistItem.Id(item.id)
    override val playlistId: YouTubePlaylist.Id get() = YouTubePlaylist.Id(item.snippet.playlistId)
    override val title: String get() = item.snippet.title
    override val thumbnailUrl: String get() = item.snippet.thumbnails?.url ?: ""
    override val videoId: YouTubeVideo.Id get() = YouTubeVideo.Id(item.snippet.resourceId.videoId)
    override val channel: YouTubeChannelTitle
        get() = YouTubeChannelTitleImpl(
            id = YouTubeChannel.Id(item.snippet.channelId),
            title = item.snippet.channelTitle,
        )
    override val description: String get() = item.snippet.description
    override val videoOwnerChannelId: YouTubeChannel.Id?
        get() = item.snippet.videoOwnerChannelId?.let { YouTubeChannel.Id(it) }
    override val publishedAt: Instant get() = item.snippet.publishedAt.toInstant()
    override fun toString(): String = item.toPrettyString()

    companion object {
        val factory: ResponseFactory<PlaylistItemListResponse, List<YouTubePlaylistItemDetail>> =
            { res, cc ->
                NetworkResponse.create(
                    item = res.items.map { PlaylistItemDetailRemote(it) },
                    cacheControl = cc,
                    nextPageToken = res.nextPageToken,
                    eTag = res.etag,
                )
            }
    }
}

private class YouTubeChannelTitleImpl(
    override val id: YouTubeChannel.Id,
    override val title: String,
) : YouTubeChannelTitle
