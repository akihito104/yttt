package com.freshdigitable.yttt.data.source.remote

import androidx.annotation.VisibleForTesting
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
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
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

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
    ): NetworkResponse<List<YouTubePlaylistItem>>

    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>>
    fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>>
    fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): NetworkResponse<List<YouTubeChannelLog>>

    companion object {
        fun create(youtube: YouTube, dateTimeProvider: DateTimeProvider): YouTubeClient =
            YouTubeClientImpl(youtube, dateTimeProvider)

        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(5)
    }
}

internal class YouTubeClientImpl(
    private val youtube: YouTube,
    private val dateTimeProvider: DateTimeProvider,
) : YouTubeClient {
    override fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?,
    ): NetworkResponse<List<YouTubeSubscription>> {
        val res = youtube.fetch {
            subscriptions()
                .list(listOf(PART_SNIPPET))
                .setMine(true)
                .setMaxResults(pageSize)
                .setPageToken(token)
        }
        return NetworkResponse.create(
            item = res.items.mapIndexed { i, s -> YouTubeSubscriptionRemote(s, offset + i) },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> {
        val res = youtube.fetch {
            channels()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS, "brandingSettings", "statistics"))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }
        val current = dateTimeProvider.now()
        val cacheControl = CacheControl.create(current, MAX_AGE_DEFAULT)
        return NetworkResponse.create(item = res.items.map { YouTubeChannelImpl(it, cacheControl) })
    }

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> {
        val res = youtube.fetch {
            playlists()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }
        val current = dateTimeProvider.now()
        val cacheControl = CacheControl.create(current, MAX_AGE_DEFAULT)
        return NetworkResponse.create(
            item = res.items.map { YouTubePlaylistRemote(it, cacheControl) },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItem>> {
        val res = youtube.fetch {
            playlistItems()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setPlaylistId(id.value)
                .setMaxResults(maxResult)
        }
        val current = dateTimeProvider.now()
        return NetworkResponse.create(
            item = res.items.map { PlaylistItemRemote(it, current) },
        )
    }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        val res = youtube.fetch {
            videos()
                .list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }
        val current = dateTimeProvider.now()
        return NetworkResponse.create(
            item = res.items.map { YouTubeVideoRemote(it, current) },
            nextPageToken = res.nextPageToken,
        )
    }

    override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> {
        val res = youtube.fetch {
            channelSections()
                .list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                .setChannelId(id.value)
        }
        return NetworkResponse.create(
            item = res.items.map { YouTubeChannelSectionImpl(it) },
        )
    }

    override fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): NetworkResponse<List<YouTubeChannelLog>> {
        check(publishedAfter != null || maxResult != null) { "publishedAfter or maxResult should not be null" }
        val res = youtube.fetch {
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
        return NetworkResponse.create(
            item = res.items.map { YouTubeChannelLogEntity(it) },
            nextPageToken = res.nextPageToken,
        )
    }

    companion object {
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
        private fun <T> YouTube.fetch(request: YouTube.() -> AbstractGoogleClientRequest<T>): T =
            try {
                request().execute()
            } catch (e: HttpResponseException) {
                throw YouTubeException(e.statusCode, e.statusMessage, e)
            }
    }
}

class YouTubeException(
    override val statusCode: Int,
    private val statusMessage: String,
    throwable: Throwable? = null,
) : NetworkResponse.Exception(throwable) {
    override val isQuotaExceeded: Boolean
        get() = statusCode == 403 && statusMessage == "quotaExceeded"
}

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

@VisibleForTesting
internal class YouTubeVideoRemote(
    private val video: Video,
    private val fetchedAt: Instant,
) : YouTubeVideo {
    private val liveStreamingDetails: VideoLiveStreamingDetails? get() = video.liveStreamingDetails
    private val snippet get() = requireNotNull(video.snippet) { "json: $video" }
    override val id: YouTubeVideo.Id = YouTubeVideo.Id(video.id)
    override val channel: YouTubeChannelTitle = YouTubeChannelTitleImpl(
        id = YouTubeChannel.Id(snippet.channelId),
        title = snippet.channelTitle,
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
    override val cacheControl: CacheControl get() = CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)

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
    override val cacheControl: CacheControl,
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

private class YouTubePlaylistRemote(
    private val playlist: Playlist,
    override val cacheControl: CacheControl,
) : YouTubePlaylist {
    override val id: YouTubePlaylist.Id get() = YouTubePlaylist.Id(playlist.id)
    override val title: String get() = playlist.snippet.title
    override val thumbnailUrl: String get() = playlist.snippet.thumbnails.url
    override fun toString(): String = playlist.toPrettyString()
}

private class PlaylistItemRemote(
    private val item: PlaylistItem,
    override val fetchedAt: Instant,
) : YouTubePlaylistItem {
    override val id: YouTubePlaylistItem.Id get() = YouTubePlaylistItem.Id(item.id)
    override val playlistId: YouTubePlaylist.Id get() = YouTubePlaylist.Id(item.snippet.playlistId)
    override val title: String get() = item.snippet.title
    override val thumbnailUrl: String get() = item.snippet.thumbnails?.url ?: ""
    override val videoId: YouTubeVideo.Id get() = YouTubeVideo.Id(item.contentDetails.videoId)
    override val channel: YouTubeChannelTitle
        get() = YouTubeChannelTitleImpl(
            id = YouTubeChannel.Id(item.snippet.channelId),
            title = item.snippet.channelTitle,
        )
    override val description: String = item.snippet.description
    override val videoOwnerChannelId: YouTubeChannel.Id? =
        item.snippet.videoOwnerChannelId?.let { YouTubeChannel.Id(it) }
    override val publishedAt: Instant get() = item.snippet.publishedAt.toInstant()
    override val maxAge: Duration get() = MAX_AGE_DEFAULT
    override fun toString(): String = item.toPrettyString()
}

private class YouTubeChannelTitleImpl(
    override val id: YouTubeChannel.Id,
    override val title: String,
) : YouTubeChannelTitle
