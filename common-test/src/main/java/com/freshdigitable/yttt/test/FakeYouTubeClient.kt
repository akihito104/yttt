package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.text
import com.freshdigitable.yttt.di.GoogleAccountModule
import com.freshdigitable.yttt.test.ChannelItemJson.Companion.toQueryString
import com.freshdigitable.yttt.test.ChannelItemJson.Companion.wrapResponseJson
import com.freshdigitable.yttt.test.Json.Companion.obj
import com.freshdigitable.yttt.test.PlaylistItemJson.Companion.eTag
import com.freshdigitable.yttt.test.SubscriptionItemJson.Companion.eTag
import com.freshdigitable.yttt.test.VideoJson.Companion.toQueryString
import com.freshdigitable.yttt.test.VideoJson.Companion.wrapResponseJson
import com.google.api.client.http.HttpRequestInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [GoogleAccountModule::class],
)
interface FakeYouTubeClientModule {
    companion object {
        @Singleton
        @Provides
        fun provideInitializer(): HttpRequestInitializer = HttpRequestInitializer {
            // NOP
        }

        @Singleton
        @Provides
        fun provideNewChooseAccountIntentProvider(): NewChooseAccountIntentProvider =
            object : NewChooseAccountIntentProvider {
                override fun invoke(): Intent = throw NotImplementedError()
            }
    }
}

private const val PATH_VIDEOS = "/youtube/v3/videos"
private const val PATH_CHANNELS = "/youtube/v3/channels"
private const val PATH_PLAYLISTS = "/youtube/v3/playlists"
private const val PATH_PLAYLIST_ITEMS = "/youtube/v3/playlistItems"
const val PATH_SUBSCRIPTION = "/youtube/v3/subscriptions"

class YouTubeMockServerDispatcher : MockServerDispatcher {
    private val responseTable = mutableMapOf<MockServerDispatcher.RequestKey, MockServerDispatcher.ExpectedResponse>()
    private val responseItemTable =
        mutableMapOf<MockServerDispatcher.RequestKey, MockServerDispatcher.ExpectedResponse>()

    override fun add(vararg res: MockServerDispatcher.ExpectedResponse) {
        responseTable.putAll(res.map { it.key to it })
    }

    fun addAsItem(vararg item: MockServerDispatcher.ExpectedResponse) {
        responseItemTable.putAll(item.map { it.key to it })
    }

    private fun getResponseItem(key: MockServerDispatcher.RequestKey): MockServerDispatcher.ExpectedResponse =
        responseItemTable.remove(key) ?: throw AssertionError("unexpected: $key, table: $responseItemTable")

    override fun dispatch(request: MockServerDispatcher.Request): ResponseJson {
        val res = responseTable.remove(request.key)
        return res ?: when (request.key.encodedPath) {
            PATH_CHANNELS -> {
                val part = request.queryParam("part") ?: throw AssertionError()
                val json = request.queryParams("id").asSequence()
                    .filterNotNull().map { YouTubeChannel.Id(it) }
                    .map { listOf(it).toQueryString(part) }
                    .map { request.key.copy(encodedQuery = it) }
                    .map(::getResponseItem)
                    .toList()
                YouTubeResponseJson(
                    kind = ChannelItemJson.KIND_CHANNEL_LIST_RESPONSE,
                    items = json,
                )
            }

            PATH_VIDEOS -> {
                val json = request.queryParams("id").asSequence()
                    .filterNotNull().map { YouTubeVideo.Id(it) }
                    .map { listOf(it).toQueryString() }
                    .map { request.key.copy(encodedQuery = it) }
                    .map(::getResponseItem)
                    .toList()
                YouTubeResponseJson(
                    kind = VideoJson.KIND_VIDEO_LIST_RESPONSE,
                    items = json,
                )
            }

            else -> throw AssertionError("unexpected: $request, table: $responseTable, itemTable: $responseItemTable")
        }
    }
}

fun CacheControl.Companion.fromRemote(fetchedAt: Instant): CacheControl =
    CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)

class YouTubeResponseJson(
    private val kind: String,
    val eTag: String = "etag",
    private val nextPageToken: String? = null,
    private val prevPageToken: String? = null,
    private val totalResults: Int = 1,
    private val items: List<Json>,
) : ResponseJson {
    override fun toString(): String = obj {
        this["kind"] = kind
        this["etag"] = eTag
        this["nextPageToken"] = nextPageToken
        this["prevPageToken"] = prevPageToken
        this["pageInfo"] = obj {
            this["totalResults"] = totalResults
            this["resultsPerPage"] = items.size
        }
        this["items"] = items
    }.toString()
}

class YouTubeErrorJson(override val statusCode: Int, private val message: String = "error") : ResponseJson {
    companion object {
        fun notFound(): YouTubeErrorJson = YouTubeErrorJson(404, "Not Found")
        val notModified: YouTubeErrorJson = YouTubeErrorJson(304, "Not Modified")
        fun internalServerError(): YouTubeErrorJson = YouTubeErrorJson(500, "Internal Server Error")
    }

    override fun toString(): String = obj {
        this["error"] = obj {
            this["errors"] = listOf(
                obj {
                    this["domain"] = "global"
                    this["reason"] = "invalidParameter"
                    this["message"] = "Invalid string value: 'asdf'. Allowed values: [mostpopular]"
                    this["locationType"] = "parameter"
                    this["location"] = "chart"
                },
            )
            this["code"] = statusCode
            this["message"] = message
        }
    }.toString()
}

class VideoJson(
    val id: YouTubeVideo.Id,
    val channelId: YouTubeChannel.Id,
    val channelTitle: String,
    val liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
    val scheduledStartDateTime: Instant? = null,
    val actualStartDateTime: Instant? = null,
    val actualEndDateTime: Instant? = null,
) : Json {
    constructor(video: YouTubeVideo) : this(
        video.id,
        video.channel.id,
        video.channel.title,
        video.liveBroadcastContent,
        video.scheduledStartDateTime,
        video.actualStartDateTime,
        video.actualEndDateTime,
    )

    constructor(
        idNum: Int,
        channel: YouTubeChannelTitle,
        liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
        scheduledStartDateTime: Instant? = null,
        actualStartDateTime: Instant? = null,
        actualEndDateTime: Instant? = null,
    ) : this(
        YouTubeVideo.Id("${channel.id.value}-video_$idNum"),
        channel.id, channel.title, liveBroadcastContent,
        scheduledStartDateTime, actualStartDateTime, actualEndDateTime,
    )

    val json: Json
        get() = obj {
            this["kind"] = "youtube#video"
            this["etag"] = "etag"
            this["id"] = id.value
            this["snippet"] = obj {
                this["publishedAt"] = null
                this["channelId"] = channelId.value
                this["title"] = "title${id.value}"
                this["description"] = "description"
                this["thumbnails"] = thumbnails
                this["channelTitle"] = channelTitle
                this["tags"] = emptyList<String>()
                this["categoryId"] = "categoryid${id.value}"
                this["liveBroadcastContent"] = liveBroadcastContent.name.lowercase()
                this["defaultLanguage"] = "ja"
                this["localized"] = obj {
                    this["title"] = "title${id.value}"
                    this["description"] = "description"
                }
                this["defaultAudioLanguage"] = "ja"
            }
            this["liveStreamingDetails"] = obj {
                this["actualStartTime"] = actualStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["actualEndTime"] = actualEndDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["scheduledStartTime"] = scheduledStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["scheduledEndTime"] = null
                this["concurrentViewers"] = "100"
                this["activeLiveChatId"] = "livechatid${id.value}"
            }
        }

    override fun toString(): String = json.toString()

    companion object {
        fun List<YouTubeVideo.Id>.toQueryString() = joinToString("&") { "id=${it.value}" } +
            "&maxResults=$size&part=snippet&part=liveStreamingDetails"

        fun VideoJson.toExpectedResponse(): MockServerDispatcher.ExpectedResponse =
            object : MockServerDispatcher.ExpectedResponse {
                override val key: MockServerDispatcher.RequestKey
                    get() = MockServerDispatcher.RequestKey(
                        encodedPath = PATH_VIDEOS,
                        encodedQuery = listOf(this@toExpectedResponse.id).toQueryString(),
                    )
                override val body: String get() = this@toExpectedResponse.toString()
                override fun toString(): String = body
            }

        internal const val KIND_VIDEO_LIST_RESPONSE = "youtube#videoListResponse"
        fun List<VideoJson>.wrapResponseJson(): YouTubeResponseJson = YouTubeResponseJson(
            kind = KIND_VIDEO_LIST_RESPONSE,
            items = this,
        )
    }
}

fun MockServerDispatcher.ExpectedResponse.Companion.youtubeVideo(
    items: List<VideoJson> = emptyList(),
    query: List<YouTubeVideo.Id> = items.map { it.id },
    json: ResponseJson? = null,
): MockServerDispatcher.ExpectedResponse {
    check(query.isNotEmpty())
    return create(path = PATH_VIDEOS, query = query.toQueryString(), json = json ?: items.wrapResponseJson())
}

class ChannelItemJson(
    override val id: YouTubeChannel.Id,
    override val title: String = "channel_${id.value}",
    val playlistId: YouTubePlaylist.Id? = null,
    val hasSnippet: Boolean,
    val hasContentDetail: Boolean,
) : Json, YouTubeChannelTitle {
    companion object {
        fun createSnippet(id: YouTubeChannel.Id): ChannelItemJson =
            ChannelItemJson(id = id, hasSnippet = true, hasContentDetail = false)

        fun createRelatedPlaylist(idNum: Int): ChannelItemJson = ChannelItemJson(
            id = YouTubeChannel.Id("channel_$idNum"),
            playlistId = YouTubePlaylist.Id("playlist_channel_$idNum"),
            hasSnippet = false,
            hasContentDetail = true,
        )

        fun Collection<YouTubeChannel.Id>.toQueryString(detail: Boolean): String {
            val part = if (detail) "contentDetails" else "snippet"
            return toQueryString(part)
        }

        fun Collection<YouTubeChannel.Id>.toQueryString(part: String): String =
            joinToString("&") { "id=${it.value}" } + "&maxResults=$size&part=$part"

        fun ChannelItemJson.toExpectedResponse(
            detail: Boolean,
        ): MockServerDispatcher.ExpectedResponse = object : MockServerDispatcher.ExpectedResponse {
            override val key: MockServerDispatcher.RequestKey
                get() = MockServerDispatcher.RequestKey(
                    encodedPath = PATH_CHANNELS,
                    encodedQuery = listOf(this@toExpectedResponse.id).toQueryString(detail),
                )
            override val body: String get() = this@toExpectedResponse.toString()
            override fun toString(): String = body
        }

        internal const val KIND_CHANNEL_LIST_RESPONSE = "youtube#channelListResponse"
        fun Collection<ChannelItemJson>.wrapResponseJson(): YouTubeResponseJson = YouTubeResponseJson(
            kind = KIND_CHANNEL_LIST_RESPONSE,
            items = this.toList(),
        )
    }

    private val json: Json
        get() = obj {
            this["kind"] = "youtube#channel"
            this["etag"] = "etag"
            this["id"] = id.value
            this["snippet"] = if (hasSnippet) snippet else null
            this["contentDetails"] = if (hasContentDetail) contentDetail else null
        }
    private val snippet: Json
        get() = obj {
            this["title"] = title
            this["description"] = "description"
            this["customUrl"] = ""
            this["publishedAt"] = "1970-01-01T00:00:00Z"
            this["thumbnails"] = icon
            this["defaultLanguage"] = "ja"
            this["localized"] = obj {
                this["title"] = title
                this["description"] = "description"
            }
            this["country"] = "jp"
        }
    private val contentDetail: Json
        get() = obj {
            this["relatedPlaylists"] = obj {
                this["likes"] = ""
                this["favorites"] = ""
                this["uploads"] = playlistId?.value ?: "playlist_${id.value}"
            }
        }

    override fun toString(): String = json.toString()
}

fun MockServerDispatcher.ExpectedResponse.Companion.youtubeChannel(
    items: List<ChannelItemJson> = emptyList(),
    query: List<YouTubeChannel.Id> = items.map { it.id },
    detail: Boolean = false,
    json: ResponseJson? = null,
): MockServerDispatcher.ExpectedResponse {
    check(query.isNotEmpty())
    return create(path = PATH_CHANNELS, query = query.toQueryString(detail), json = json ?: items.wrapResponseJson())
}

class PlaylistJson(val id: YouTubePlaylist.Id, val title: String = "playlist_$id") : Json {
    private val json: Json
        get() = obj {
            this["kind"] = "youtube#playlist"
            this["etag"] = "etag"
            this["id"] = id.value
            this["snippet"] = obj {
                this["publishedAt"] = "1970-01-01T00:00:00Z"
                this["channelId"] = "channel_id"
                this["title"] = title
                this["description"] = "description"
                this["thumbnails"] = thumbnails
                this["channelTitle"] = "channeltitle"
                this["defaultLanguage"] = "ja"
                this["localized"] = obj {
                    this["title"] = title
                    this["description"] = "description"
                }
            }
            this["contentDetails"] = obj {
                this["itemCount"] = 10
            }
        }

    override fun toString(): String = json.toString()
}

fun MockServerDispatcher.ExpectedResponse.Companion.youtubePlaylist(
    items: List<PlaylistJson>,
    query: List<YouTubePlaylist.Id> = items.map { it.id },
): MockServerDispatcher.ExpectedResponse {
    val q = query.joinToString("&") { "id=${it.value}" } + "&maxResults=${query.size}&part=snippet&part=contentDetails"
    val j = YouTubeResponseJson(
        kind = "youtube#playlistListResponse",
        items = items,
    )
    return create(path = PATH_PLAYLISTS, query = q, json = j)
}

class PlaylistItemJson(
    val id: YouTubePlaylistItem.Id,
    val playlistId: YouTubePlaylist.Id,
    val videoId: YouTubeVideo.Id = YouTubeVideo.Id("video_${id}_$playlistId"),
) : Json {
    companion object {
        fun List<PlaylistItemJson>.eTag(): String = joinToString(",") { it.key }.sha1()
    }

    val key: String = listOf(id, playlistId, videoId).joinToString { it.value }
    private val json: String = obj {
        this["kind"] = "youtube#playlistItem"
        this["etag"] = "etag"
        this["id"] = id.value
        this["contentDetails"] = obj {
            this["videoId"] = videoId.value
            this["startAt"] = null
            this["endAt"] = null
            this["note"] = null
            this["videoPublishedAt"] = "1970-01-01T00:00:00.000Z"
        }
    }.toString()

    override fun toString(): String = json
}

fun MockServerDispatcher.ExpectedResponse.Companion.youtubePlaylistItem(
    items: List<PlaylistItemJson> = emptyList(),
    eTag: String? = null,
    query: YouTubePlaylist.Id? = null,
    json: ResponseJson? = null,
): MockServerDispatcher.ExpectedResponse {
    val playlistIds = items.map { it.playlistId }.toSet()
    check(playlistIds.size == 1 || query != null) { "playlistIds: $playlistIds" }
    val q = "maxResults=10&part=contentDetails&playlistId=${query?.value ?: playlistIds.first().value}"
    val j = json ?: YouTubeResponseJson(
        kind = "youtube#playlistItemListResponse",
        eTag = items.eTag(),
        items = items,
    )
    return create(path = PATH_PLAYLIST_ITEMS, eTag = eTag, query = q, json = j)
}

class SubscriptionItemJson(val id: String, val channelId: String, val channelTitle: String) : Json {
    companion object {
        fun List<SubscriptionItemJson>.eTag(): String = joinToString(",") { it.key }.sha1()
    }

    val key: String = listOf(id, channelId, channelTitle).joinToString(",") { it }
    private val json
        get() = obj {
            this["kind"] = "youtube#subscription"
            this["etag"] = key.sha1()
            this["id"] = id
            this["snippet"] = obj {
                this["publishedAt"] = "1970-01-01T00:00:00Z"
                this["channelTitle"] = channelTitle
                this["title"] = channelTitle
                this["description"] = "description"
                this["resourceId"] = obj {
                    this["kind"] = "string"
                    this["channelId"] = channelId
                }
                this["channelId"] = channelId
                this["thumbnails"] = icon
            }
        }

    override fun toString(): String = json.toString()
}

fun MockServerDispatcher.ExpectedResponse.Companion.youtubeSubscription(
    eTag: String? = null,
    token: String? = null,
    order: YouTubeSubscriptionQuery.Order,
    nextPageToken: String? = null,
    items: List<SubscriptionItemJson> = emptyList(),
    json: ResponseJson? = null,
): MockServerDispatcher.ExpectedResponse {
    val pageToken = token?.let { "&pageToken=$it" } ?: ""
    val query = "maxResults=50&mine=true&order=${order.text}$pageToken&part=snippet"
    val j = json ?: YouTubeResponseJson(
        kind = "youtube#subscriptionListResponse",
        eTag = items.eTag(),
        nextPageToken = nextPageToken,
        items = items,
    )
    return create(path = PATH_SUBSCRIPTION, eTag = eTag, query = query, json = j)
}

private val md = MessageDigest.getInstance("SHA-1")
private fun String.sha1(): String = md.run {
    reset()
    digest(toByteArray()).toHexString()
}

private val icon = obj {
    this["medium"] = obj {
        this["url"] = "<url is here>"
        this["width"] = 240
        this["height"] = 240
    }
}
private val thumbnails = obj {
    this["standard"] = obj {
        this["url"] = "<url is here>"
        this["width"] = 480
        this["height"] = 320
    }
}
