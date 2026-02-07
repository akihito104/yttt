package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.GoogleAccountModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.test.FakeYouTubeClient.Companion.currentDate
import com.freshdigitable.yttt.test.Json.Companion.sha1
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
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.math.BigInteger
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
    fun fetchSubscription(nextPageToken: String? = null, order: String): YouTubeResponseJson =
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

    companion object {
        val currentDate: String
            get() = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"))
                .format(FakeDateTimeProviderModule.instant)

        fun channelTitle(id: Int = 1): YouTubeChannelTitle = object : YouTubeChannelTitle {
            override val id: YouTubeChannel.Id get() = YouTubeChannel.Id("channel_$id")
            override val title: String get() = "channel_$id"
        }

        fun channelDetail(
            id: Int = 1,
            idValue: String = "channel_$id",
        ): YouTubeChannelDetail = object : YouTubeChannelDetail {
            override val id: YouTubeChannel.Id get() = YouTubeChannel.Id(idValue)
            override val uploadedPlayList: YouTubePlaylist.Id
                get() = YouTubePlaylist.Id("playlist_${this.id.value}")
            override val title: String get() = "channel_$idValue"
            override val iconUrl: String get() = "<url is here>"
            override val bannerUrl: String get() = ""
            override val subscriberCount: BigInteger get() = BigInteger.ONE
            override val isSubscriberHidden: Boolean get() = false
            override val videoCount: BigInteger get() = BigInteger.ONE
            override val viewsCount: BigInteger get() = BigInteger.ONE
            override val publishedAt: Instant get() = Instant.EPOCH
            override val customUrl: String get() = ""
            override val keywords: Collection<String> get() = emptyList()
            override val description: String get() = ""
        }

        fun playlist(id: YouTubePlaylist.Id): YouTubePlaylist = object : YouTubePlaylist {
            override val id: YouTubePlaylist.Id get() = id
            override val title: String get() = "playlist_${id.value}"
            override val thumbnailUrl: String get() = ""
        }

        fun playlistItem(
            id: YouTubePlaylistItem.Id,
            playlistId: YouTubePlaylist.Id,
            videoId: YouTubeVideo.Id = YouTubeVideo.Id("video_${id.value}_${playlistId.value}"),
            publishedAt: Instant = Instant.EPOCH,
        ): YouTubePlaylistItem = object : YouTubePlaylistItem {
            override val id: YouTubePlaylistItem.Id get() = id
            override val playlistId: YouTubePlaylist.Id get() = playlistId
            override val videoId: YouTubeVideo.Id get() = videoId
            override val publishedAt: Instant get() = publishedAt
        }

        fun playlistItemDetail(
            id: YouTubePlaylistItem.Id,
            playlistId: YouTubePlaylist.Id,
            channel: YouTubeChannelTitle = object : YouTubeChannelTitle {
                override val id: YouTubeChannel.Id get() = YouTubeChannel.Id("channel_0")
                override val title: String get() = "Channel"
            },
            videoId: YouTubeVideo.Id = YouTubeVideo.Id("video_${id.value}_${playlistId.value}"),
            publishedAt: Instant = Instant.EPOCH,
        ): YouTubePlaylistItemDetail = YouTubePlaylistItemDetailEntity(
            id = id,
            playlistId = playlistId,
            title = "title",
            channel = channel,
            thumbnailUrl = "",
            videoId = videoId,
            description = "",
            videoOwnerChannelId = null,
            publishedAt = publishedAt,
        )
    }
}

fun CacheControl.Companion.fromRemote(fetchedAt: Instant): CacheControl =
    CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)

private data class YouTubePlaylistItemDetailEntity(
    override val id: YouTubePlaylistItem.Id,
    override val playlistId: YouTubePlaylist.Id,
    override val title: String,
    override val channel: YouTubeChannelTitle,
    override val thumbnailUrl: String,
    override val videoId: YouTubeVideo.Id,
    override val description: String,
    override val videoOwnerChannelId: YouTubeChannel.Id?,
    override val publishedAt: Instant,
) : YouTubePlaylistItemDetail

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
    }

    private val server = MockWebServer()
    override fun starting(description: Description) {
        server.start()
        YouTubeModule.rootUrl = server.url("").toUrl().toString()
    }

    fun setClient(client: FakeYouTubeClient) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = runCatching {
                when (val path = request.url.encodedPath) {
                    "/youtube/v3/videos" -> {
                        val id = request.url.queryParameterValues("id")
                            .mapNotNull { i -> i?.let { YouTubeVideo.Id(it) } }
                        val items = client.fetchVideoList(id.toSet())
                        videoJson(items)
                    }

                    "/youtube/v3/channels" -> {
                        val id = requireNotNull(request.url.queryParameterValues("id"))
                            .mapNotNull { i -> i?.let { YouTubeChannel.Id(it) } }.toSet()
                        val part = request.url.queryParameterValues("part").filterNotNull().toSet()
                        val res = client.fetchChannels(id, part)
                        channelJson(res)
                    }

                    "/youtube/v3/playlists" -> {
                        val id = requireNotNull(request.url.queryParameter("id"))
                        val res = client.fetchPlaylist(setOf(YouTubePlaylist.Id(id)))
                        playlistJson(res)
                    }

                    "/youtube/v3/playlistItems" -> {
                        val id = requireNotNull(request.url.queryParameter("playlistId"))
                        val maxResult = requireNotNull(request.url.queryParameter("maxResults")).toLong()
                        val eTag = request.headers["If-None-Match"]
                        val res = client.fetchPlaylistItems(YouTubePlaylist.Id(id), maxResult, eTag)
                        playlistItemJson(res)
                    }

                    PATH_SUBSCRIPTION -> {
                        check(request.url.queryParameter("mine").toBoolean())
                        val eTag = request.headers["If-None-Match"]
                        val pageToken = request.url.queryParameter("pageToken")
                        val order = requireNotNull(request.url.queryParameter("order"))
                        val res = client.fetchSubscription(nextPageToken = pageToken, order = order)
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
) : Json() {
    override fun toString(): String = Obj(
        "kind" to kind,
        "etag" to eTag,
        "nextPageToken" to nextPageToken,
        "prevPageToken" to prevPageToken,
        "pageInfo" to Obj(
            "totalResults" to totalResults,
            "resultsPerPage" to items.size,
        ),
        "items" to Arr(items),
    ).toString()
}

private fun videoJson(items: List<VideoJson>): Json = YouTubeResponseJson(
    kind = "youtube#videoListResponse",
    eTag = "etag",
    items = items,
)

class VideoJson(
    val id: YouTubeVideo.Id,
    val channel: YouTubeChannelTitle,
    val liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
    val scheduledStartDateTime: Instant? = null,
    val actualStartDateTime: Instant? = null,
    val actualEndDateTime: Instant? = null,
) : Json() {
    constructor(video: YouTubeVideo) : this(
        video.id,
        video.channel,
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
        channel, liveBroadcastContent,
        scheduledStartDateTime, actualStartDateTime, actualEndDateTime,
    )

    val json: Json
        get() = Obj(
            "kind" to "youtube#video",
            "etag" to "etag",
            "id" to id.value,
            "snippet" to Obj(
                "publishedAt" to null,
                "channelId" to channel.id.value,
                "title" to "title${id.value}",
                "description" to "description",
                "thumbnails" to Obj(
                    "standard" to Obj("url" to "<url is here>", "width" to 480, "height" to 360),
                ),
                "channelTitle" to channel.title,
                "tags" to Arr(emptyList()),
                "categoryId" to "categoryid${id.value}",
                "liveBroadcastContent" to liveBroadcastContent.name.lowercase(),
                "defaultLanguage" to "ja",
                "localized" to Obj(
                    "title" to "title${id.value}",
                    "description" to "description",
                ),
                "defaultAudioLanguage" to "ja",
            ),
            "liveStreamingDetails" to Obj(
                "actualStartTime" to actualStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "actualEndTime" to actualEndDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "scheduledStartTime" to scheduledStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "scheduledEndTime" to null,
                "concurrentViewers" to "100",
                "activeLiveChatId" to "livechatid${id.value}",
            ),
        )

    override fun toString(): String = json.toString()
}

private fun channelJson(items: List<ChannelItemJson>): YouTubeResponseJson = YouTubeResponseJson(
    kind = "youtube#channelListResponse",
    eTag = "etag",
    items = items,
)

class ChannelItemJson(
    override val id: YouTubeChannel.Id,
    override val title: String = "channel_$id",
    private val playlistId: String? = null,
    val hasSnippet: Boolean,
    val hasContentDetail: Boolean,
) : Json(), YouTubeChannelTitle {
    constructor(id: YouTubeChannel.Id, part: Set<String>) : this(
        id = id,
        hasSnippet = part.contains("snippet"),
        hasContentDetail = part.contains("contentDetails"),
    )

    constructor(channel: YouTubeChannelDetail, part: Set<String>) : this(
        id = channel.id,
        title = channel.title,
        playlistId = channel.uploadedPlayList!!.value,
        hasSnippet = part.contains("snippet"),
        hasContentDetail = part.contains("contentDetails"),
    )

    private val json: Json
        get() = Obj(
            "kind" to "youtube#channel",
            "etag" to "etag",
            "id" to id.value,
            "snippet" to if (hasSnippet) snippet else null,
            "contentDetails" to if (hasContentDetail) contentDetail else null,
        )
    private val snippet: Json
        get() = Obj(
            "title" to title,
            "description" to "description",
            "customUrl" to "",
            "publishedAt" to "1970-01-01T00:00:00Z",
            "thumbnails" to Obj(
                "medium" to Obj(
                    "url" to "<url is here>",
                    "width" to 240,
                    "height" to 240,
                ),
            ),
            "defaultLanguage" to "ja",
            "localized" to Obj(
                "title" to title,
                "description" to "description",
            ),
            "country" to "jp",
        )
    private val contentDetail: Json
        get() = Obj(
            "relatedPlaylists" to Obj(
                "likes" to "",
                "favorites" to "",
                "uploads" to (playlistId ?: "playlist_${id.value}"),
            ),
        )

    override fun toString(): String = json.toString()
}

private fun playlistJson(items: List<PlaylistJson>): Json = YouTubeResponseJson(
    kind = "youtube#playlistListResponse",
    items = items,
)

class PlaylistJson(val id: String, val title: String = "playlist_$id") : Json() {
    private val json: Json
        get() = Obj(
            "kind" to "youtube#playlist",
            "etag" to "etag",
            "id" to id,
            "snippet" to Obj(
                "publishedAt" to "1970-01-01T00:00:00Z",
                "channelId" to "channel_id",
                "title" to title,
                "description" to "description",
                "thumbnails" to Obj(
                    "standard" to Obj(
                        "url" to "<url is here>",
                        "width" to 480,
                        "height" to 320,
                    ),
                ),
                "channelTitle" to "channeltitle",
                "defaultLanguage" to "ja",
                "localized" to Obj(
                    "title" to title,
                    "description" to "description",
                ),
            ),
            "contentDetails" to Obj(
                "itemCount" to 10,
            ),
        )

    override fun toString(): String = json.toString()
}

private fun playlistItemJson(items: List<PlaylistItemJson>): Json = YouTubeResponseJson(
    kind = "youtube#playlistItemListResponse",
    items = items,
)

class PlaylistItemJson(
    val id: String,
    val playlistId: YouTubePlaylist.Id,
    val videoId: String = "video_${id}_${playlistId.value}",
    val publishedAt: Instant = Instant.EPOCH,
) : Json() {
    private val json: Json
        get() = Obj(
            "kind" to "youtube#playlistItem",
            "etag" to "etag",
            "id" to id,
            "contentDetails" to Obj(
                "videoId" to videoId,
                "startAt" to "",
                "endAt" to "",
                "note" to "",
                "videoPublishedAt" to "$publishedAt",
            ),
        )

    override fun toString(): String = json.toString()
}

fun subscriptionJson(
    eTag: String? = null,
    pageToken: String?,
    size: Int,
    factory: (Int) -> SubscriptionItemJson = { throw NotImplementedError() },
): YouTubeResponseJson = subscriptionJson(eTag, pageToken, (0 until size).map(factory))

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

class SubscriptionItemJson(val id: String, val channelId: String, val channelTitle: String) : Json() {
    val key: String = listOf(id, channelId, channelTitle).joinToString(",") { it }
    private val json
        get() = Obj(
            "kind" to "youtube#subscription",
            "etag" to key.sha1(),
            "id" to id,
            "snippet" to Obj(
                "publishedAt" to "1970-01-01T00:00:00Z",
                "channelTitle" to channelTitle,
                "title" to channelTitle,
                "description" to "description",
                "resourceId" to Obj(
                    "kind" to "string",
                    "channelId" to channelId,
                ),
                "channelId" to channelId,
                "thumbnails" to Obj(
                    "medium" to Obj(
                        "url" to "<url is here>",
                        "width" to 240,
                        "height" to 240,
                    ),
                ),
            ),
        )

    override fun toString(): String = json.toString()
}

sealed class Json {
    companion object {
        private val md = MessageDigest.getInstance("SHA-1")
        fun String.sha1(): String = md.run {
            reset()
            digest(toByteArray()).toHexString()
        }
    }

    class Obj(val map: Map<String, Any>) : Json() {
        constructor(vararg pairs: Pair<String, Any?>) : this(
            pairs.filter { it.second != null }.associate {
                it.first to when (val v = it.second) {
                    is String -> Str(v)
                    is Number -> Num(v)
                    is Boolean -> Bool(v)
                    is Json -> v
                    else -> throw AssertionError()
                }
            },
        )

        override fun toString(): String = map.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    class Arr(val list: List<Json>) : Json() {
        override fun toString(): String = list.joinToString(",", "[", "]")
    }

    class Str(val str: String) : Json() {
        override fun toString(): String = "\"$str\""
    }

    class Num(val num: Number) : Json() {
        override fun toString(): String = "$num"
    }

    class Bool(val bool: Boolean) : Json() {
        override fun toString(): String = "$bool"
    }
}
