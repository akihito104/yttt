package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.GoogleAccountModule
import com.freshdigitable.yttt.di.OkHttpModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.test.Json.Companion.icon
import com.freshdigitable.yttt.test.Json.Companion.sha1
import com.freshdigitable.yttt.test.Json.Companion.thumbnails
import com.google.api.client.http.HttpRequestInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.internal.closeQuietly
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
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

interface FakeYouTubeClient {
    fun fetchSubscription(nextPageToken: String? = null, order: YouTubeSubscriptionQuery.Order): YouTubeResponseJson =
        throw NotImplementedError()

    fun fetchChannels(ids: Set<YouTubeChannel.Id>, part: Set<String>): List<ChannelItemJson> =
        throw NotImplementedError()

    fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<PlaylistJson> = throw NotImplementedError()

    fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): List<PlaylistItemJson> = throw NotImplementedError()

    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<VideoJson> = throw NotImplementedError()
}

fun CacheControl.Companion.fromRemote(fetchedAt: Instant): CacheControl =
    CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)

fun YouTubeException.Companion.notModified(
    throwable: Throwable? = null,
    cacheControl: CacheControl = CacheControl.EMPTY,
): YouTubeException = YouTubeException(304, "Not Modified", throwable, cacheControl)

fun YouTubeException.Companion.notFound(
    throwable: Throwable? = null,
    cacheControl: CacheControl = CacheControl.EMPTY,
): YouTubeException = YouTubeException(404, "Not Found", throwable, cacheControl)

fun YouTubeException.Companion.internalServerError(
    throwable: Throwable? = null,
    cacheControl: CacheControl = CacheControl.EMPTY,
): YouTubeException = YouTubeException(500, "Internal Server Error", throwable, cacheControl)

class MockServerRule : TestWatcher() {
    companion object {
        const val PATH_SUBSCRIPTION = "/youtube/v3/subscriptions"
        private val ZONE_ID_GMT: ZoneId = ZoneId.of("GMT")
        private val currentDate: String
            get() = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZONE_ID_GMT)
                .format(FakeDateTimeProviderModule.instant)
    }

    private val server = MockWebServer()
    override fun starting(description: Description) {
        OkHttpModule.logLevel = HttpLoggingInterceptor.Level.NONE
        server.start()
        YouTubeModule.rootUrl = server.url("").toUrl().toString()
    }

    fun setClient(
        videoList: ((Set<YouTubeVideo.Id>) -> List<VideoJson>)? = null,
        channelList: ((Pair<Set<YouTubeChannel.Id>, Set<String>>) -> List<ChannelItemJson>)? = null,
        playlist: ((Set<YouTubePlaylist.Id>) -> List<PlaylistJson>)? = null,
        playlistItems: ((Pair<YouTubePlaylist.Id, String?>) -> List<PlaylistItemJson>)? = null,
        subscription: ((String?, YouTubeSubscriptionQuery.Order) -> YouTubeResponseJson)? = null,
    ) {
        setClient(
            object : FakeYouTubeClient {
                override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<VideoJson> = videoList!!(ids)
                override fun fetchChannels(ids: Set<YouTubeChannel.Id>, part: Set<String>): List<ChannelItemJson> =
                    channelList!!(ids to part)

                override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<PlaylistJson> = playlist!!(ids)
                override fun fetchPlaylistItems(
                    id: YouTubePlaylist.Id,
                    maxResult: Long,
                    eTag: String?,
                ): List<PlaylistItemJson> = playlistItems!!(id to eTag)

                override fun fetchSubscription(
                    nextPageToken: String?,
                    order: YouTubeSubscriptionQuery.Order,
                ): YouTubeResponseJson = subscription!!(nextPageToken, order)
            },
        )
    }

    fun setClient(client: FakeYouTubeClient) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = runCatching {
                when (val path = request.url.encodedPath) {
                    "/youtube/v3/videos" -> {
                        val id = request.url.queryParameterValues("id")
                            .mapNotNull { i -> i?.let { YouTubeVideo.Id(it) } }
                        client.fetchVideoList(id.toSet()).responseJson()
                    }

                    "/youtube/v3/channels" -> {
                        val id = requireNotNull(request.url.queryParameterValues("id"))
                            .mapNotNull { i -> i?.let { YouTubeChannel.Id(it) } }.toSet()
                        val part = request.url.queryParameterValues("part").filterNotNull().toSet()
                        client.fetchChannels(id, part).responseJson()
                    }

                    "/youtube/v3/playlists" -> {
                        val id = requireNotNull(request.url.queryParameter("id"))
                        client.fetchPlaylist(setOf(YouTubePlaylist.Id(id))).responseJson()
                    }

                    "/youtube/v3/playlistItems" -> {
                        val id = requireNotNull(request.url.queryParameter("playlistId"))
                        val maxResult = requireNotNull(request.url.queryParameter("maxResults")).toLong()
                        val eTag = request.headers["If-None-Match"]
                        client.fetchPlaylistItems(YouTubePlaylist.Id(id), maxResult, eTag).responseJson()
                    }

                    PATH_SUBSCRIPTION -> {
                        check(request.url.queryParameter("mine").toBoolean())
                        val eTag = request.headers["If-None-Match"]
                        val pageToken = request.url.queryParameter("pageToken")
                        val order = requireNotNull(request.url.queryParameter("order"))
                        val o = YouTubeSubscriptionQuery.Order.valueOf(order.uppercase())
                        val res = client.fetchSubscription(nextPageToken = pageToken, order = o)
                        if (res.eTag == eTag) {
                            throw YouTubeException.notModified()
                        } else {
                            res
                        }
                    }

                    else -> throw AssertionError("unexpected path: $path")
                }
            }.also {
                if (isLogging) reqRes.add(request to it)
            }.fold(
                onSuccess = {
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("date", currentDate)
                        .body(it.toString())
                        .build()
                },
                onFailure = {
                    val code = (it as? YouTubeException)?.statusCode ?: 500
                    MockResponse.Builder()
                        .code(code)
                        .addHeader("date", currentDate)
                        .body(it.message.toString())
                        .build()
                },
            )
        }
    }

    var isLogging: Boolean = false
    private val reqRes: MutableList<Pair<RecordedRequest, Result<Json>>> = mutableListOf()
    fun findRecordedResponse(path: String): List<Pair<String, Result<Json>>> {
        return reqRes.filter { it.first.url.encodedPath == path }.map { it.first.url.encodedPath to it.second }
    }

    override fun finished(description: Description) {
        server.closeQuietly()
    }
}

class YouTubeResponseJson(
    private val kind: String,
    val eTag: String = "etag",
    private val nextPageToken: String? = null,
    private val prevPageToken: String? = null,
    private val totalResults: Int = 1,
    private val items: List<Json>,
) : Json {
    override fun toString(): String = Json.obj {
        this["kind"] = kind
        this["etag"] = eTag
        this["nextPageToken"] = nextPageToken
        this["prevPageToken"] = prevPageToken
        this["pageInfo"] = Json.obj {
            this["totalResults"] = totalResults
            this["resultsPerPage"] = items.size
        }
        this["items"] = Json.Arr(items)
    }.toString()
}

private inline fun <reified T : Json> List<T>.responseJson(): YouTubeResponseJson {
    val kind = when (T::class) {
        VideoJson::class -> "youtube#videoListResponse"
        ChannelItemJson::class -> "youtube#channelListResponse"
        PlaylistJson::class -> "youtube#playlistListResponse"
        PlaylistItemJson::class -> "youtube#playlistItemListResponse"
        SubscriptionItemJson::class -> "youtube#subscriptionListResponse"
        else -> error("unsupported: ${T::class}")
    }
    return YouTubeResponseJson(kind = kind, items = this)
}

fun subscriptionJson(
    eTag: String? = null,
    pageToken: String?,
    items: List<SubscriptionItemJson>,
): YouTubeResponseJson = YouTubeResponseJson(
    kind = "youtube#subscriptionListResponse",
    eTag = eTag ?: items.joinToString(",") { it.key }.sha1(),
    nextPageToken = pageToken,
    items = items,
)

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
        get() = Json.obj {
            this["kind"] = "youtube#video"
            this["etag"] = "etag"
            this["id"] = id.value
            this["snippet"] = Json.obj {
                this["publishedAt"] = null
                this["channelId"] = channelId.value
                this["title"] = "title${id.value}"
                this["description"] = "description"
                this["thumbnails"] = thumbnails
                this["channelTitle"] = channelTitle
                this["tags"] = Json.Arr(emptyList())
                this["categoryId"] = "categoryid${id.value}"
                this["liveBroadcastContent"] = liveBroadcastContent.name.lowercase()
                this["defaultLanguage"] = "ja"
                this["localized"] = Json.obj {
                    this["title"] = "title${id.value}"
                    this["description"] = "description"
                }
                this["defaultAudioLanguage"] = "ja"
            }
            this["liveStreamingDetails"] = Json.obj {
                this["actualStartTime"] = actualStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["actualEndTime"] = actualEndDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["scheduledStartTime"] = scheduledStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
                this["scheduledEndTime"] = null
                this["concurrentViewers"] = "100"
                this["activeLiveChatId"] = "livechatid${id.value}"
            }
        }

    override fun toString(): String = json.toString()
}

class ChannelItemJson(
    override val id: YouTubeChannel.Id,
    override val title: String = "channel_${id.value}",
    val playlistId: String? = null,
    val hasSnippet: Boolean,
    val hasContentDetail: Boolean,
) : Json, YouTubeChannelTitle {
    companion object {
        fun createSnippet(id: YouTubeChannel.Id): ChannelItemJson =
            ChannelItemJson(id = id, hasSnippet = true, hasContentDetail = false)

        fun createRelatedPlaylist(idNum: Int): ChannelItemJson = ChannelItemJson(
            id = YouTubeChannel.Id("channel_$idNum"),
            playlistId = "playlist_channel_$idNum",
            hasSnippet = false,
            hasContentDetail = true,
        )
    }

    private val json: Json
        get() = Json.obj {
            this["kind"] = "youtube#channel"
            this["etag"] = "etag"
            this["id"] = id.value
            this["snippet"] = if (hasSnippet) snippet else null
            this["contentDetails"] = if (hasContentDetail) contentDetail else null
        }
    private val snippet: Json
        get() = Json.obj {
            this["title"] = title
            this["description"] = "description"
            this["customUrl"] = ""
            this["publishedAt"] = "1970-01-01T00:00:00Z"
            this["thumbnails"] = icon
            this["defaultLanguage"] = "ja"
            this["localized"] = Json.obj {
                this["title"] = title
                this["description"] = "description"
            }
            this["country"] = "jp"
        }
    private val contentDetail: Json
        get() = Json.obj {
            this["relatedPlaylists"] = Json.obj {
                this["likes"] = ""
                this["favorites"] = ""
                this["uploads"] = playlistId ?: "playlist_${id.value}"
            }
        }

    override fun toString(): String = json.toString()
}

class PlaylistJson(val id: String, val title: String = "playlist_$id") : Json {
    private val json: Json
        get() = Json.obj {
            this["kind"] = "youtube#playlist"
            this["etag"] = "etag"
            this["id"] = id
            this["snippet"] = Json.obj {
                this["publishedAt"] = "1970-01-01T00:00:00Z"
                this["channelId"] = "channel_id"
                this["title"] = title
                this["description"] = "description"
                this["thumbnails"] = thumbnails
                this["channelTitle"] = "channeltitle"
                this["defaultLanguage"] = "ja"
                this["localized"] = Json.obj {
                    this["title"] = title
                    this["description"] = "description"
                }
            }
            this["contentDetails"] = Json.obj {
                this["itemCount"] = 10
            }
        }

    override fun toString(): String = json.toString()
}

class PlaylistItemJson(
    val id: String,
    val playlistId: String,
    val videoId: String = "video_${id}_$playlistId",
) : Json {
    private val json: String = Json.obj {
        this["kind"] = "youtube#playlistItem"
        this["etag"] = "etag"
        this["id"] = id
        this["contentDetails"] = Json.obj {
            this["videoId"] = videoId
            this["startAt"] = null
            this["endAt"] = null
            this["note"] = null
            this["videoPublishedAt"] = "1970-01-01T00:00:00.000Z"
        }
    }.toString()

    override fun toString(): String = json
}

class SubscriptionItemJson(val id: String, val channelId: String, val channelTitle: String) : Json {
    val key: String = listOf(id, channelId, channelTitle).joinToString(",") { it }
    private val json
        get() = Json.obj {
            this["kind"] = "youtube#subscription"
            this["etag"] = key.sha1()
            this["id"] = id
            this["snippet"] = Json.obj {
                this["publishedAt"] = "1970-01-01T00:00:00Z"
                this["channelTitle"] = channelTitle
                this["title"] = channelTitle
                this["description"] = "description"
                this["resourceId"] = Json.obj {
                    this["kind"] = "string"
                    this["channelId"] = channelId
                }
                this["channelId"] = channelId
                this["thumbnails"] = icon
            }
        }

    override fun toString(): String = json.toString()
}

sealed interface Json {
    companion object {
        private val md = MessageDigest.getInstance("SHA-1")
        fun String.sha1(): String = md.run {
            reset()
            digest(toByteArray()).toHexString()
        }

        fun obj(body: Base.() -> Unit): Obj {
            val map = Base()
            body(map)
            return Obj(map.map)
        }

        val icon = obj {
            this["medium"] = obj {
                this["url"] = "<url is here>"
                this["width"] = 240
                this["height"] = 240
            }
        }
        val thumbnails = obj {
            this["standard"] = obj {
                this["url"] = "<url is here>"
                this["width"] = 480
                this["height"] = 320
            }
        }
    }

    @JvmInline
    value class Base(val map: MutableMap<String, Any> = mutableMapOf()) : Json {
        operator fun set(key: String, value: Any?) {
            when (value) {
                null -> return
                is String -> map[key] = Str(value)
                is Number -> map[key] = Num(value)
                is Boolean -> map[key] = Bool(value)
                is Json -> map[key] = value
                else -> throw AssertionError()
            }
        }
    }

    @JvmInline
    value class Obj(val map: Map<String, Any>) : Json {
        override fun toString(): String = map.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    @JvmInline
    value class Arr(val list: List<Json>) : Json {
        override fun toString(): String = list.joinToString(",", "[", "]")
    }

    @JvmInline
    value class Str(val str: String) : Json {
        override fun toString(): String = "\"$str\""
    }

    @JvmInline
    value class Num(val num: Number) : Json {
        override fun toString(): String = "$num"
    }

    @JvmInline
    value class Bool(val bool: Boolean) : Json {
        override fun toString(): String = "$bool"
    }
}
