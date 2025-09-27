package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.PART_CONTENT_DETAILS
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.PART_SNIPPET
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.ActivityListResponse
import com.google.api.services.youtube.model.ChannelSection
import com.google.api.services.youtube.model.ChannelSectionListResponse
import com.google.api.services.youtube.model.ChannelSectionSnippet
import com.google.api.services.youtube.model.ThumbnailDetails
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

interface YouTubeClient : YouTubeSubscriptionClient, YouTubeChannelClient, YouTubePlaylistClient, YouTubeVideoClient {
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
        const val PART_SNIPPET = "snippet"
        const val PART_CONTENT_DETAILS = "contentDetails"
        const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
        val YouTubeSubscriptionQuery.Order.text: String
            get() = when (this) {
                YouTubeSubscriptionQuery.Order.ALPHABETICAL -> "alphabetical"
                YouTubeSubscriptionQuery.Order.RELEVANCE -> "relevance"
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

internal fun DateTime.toInstant(): Instant = Instant.ofEpochMilli(value)

/**
 * maxres: 720p, standard: 640x480, high: 480x360, medium: 240x180, default: 120x90.
 *
 * if original thumbnail size is `maxres` (16:9), sizes below `standard` (4:3) will have black bands at top and bottom.
 * because of aspect ratio, maxres is removed from url candidate.
 */
internal val ThumbnailDetails.url: String
    get() = (standard ?: high ?: medium ?: default)?.url ?: ""

/**
 * high: 800px, medium: 240px, default: 88px
 */
internal val ThumbnailDetails.iconUrl: String
    get() = (medium ?: high ?: default)?.url ?: ""

internal class YouTubeChannelTitleImpl(
    override val id: YouTubeChannel.Id,
    override val title: String,
) : YouTubeChannelTitle

internal class YouTubeClientImpl(private val youtube: YouTube) :
    YouTubeClient,
    YouTubeVideoClient by YouTubeVideoClientImpl(youtube),
    YouTubeSubscriptionClient by YouTubeSubscriptionClientImpl(youtube),
    YouTubeChannelClient by YouTubeChannelClientImpl(youtube),
    YouTubePlaylistClient by YouTubePlaylistClientImpl(youtube) {
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
        internal fun <R, T> YouTube.fetch(
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

private data class YouTubeChannelSectionImpl(
    private val channelSection: ChannelSection,
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
                YouTubeChannelSection.Type.MULTIPLE_PLAYLIST,
                -> YouTubeChannelSection.Content.Playlist(
                    contentDetails.playlists.map { YouTubePlaylist.Id(it) },
                )

                YouTubeChannelSection.Type.MULTIPLE_CHANNEL -> YouTubeChannelSection.Content.Channels(
                    contentDetails.channels.map { YouTubeChannel.Id(it) },
                )

                else -> error("unknown type: $snippet")
            }
        }
    }
}
