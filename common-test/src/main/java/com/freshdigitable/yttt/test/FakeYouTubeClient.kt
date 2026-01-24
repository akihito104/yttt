package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelBase
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.GoogleAccountModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.test.FakeYouTubeClient.Companion.currentDate
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

abstract class FakeYouTubeClient : YouTubeClient {
    override fun fetchSubscription(query: YouTubeSubscriptionQuery): NetworkResponse<List<YouTubeSubscription>> =
        throw NotImplementedError()

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannel>> =
        throw NotImplementedError()

    override fun fetchChannelDetailList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
        throw NotImplementedError()

    override fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): NetworkResponse<List<YouTubeChannelRelatedPlaylist>> = throw NotImplementedError()

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> =
        throw NotImplementedError()

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): NetworkResponse<List<YouTubePlaylistItem>> = throw NotImplementedError()

    override fun fetchPlaylistItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItemDetail>> = throw NotImplementedError()

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> =
        throw NotImplementedError()

    override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> =
        throw NotImplementedError()

    override fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?,
    ): NetworkResponse<List<YouTubeChannelLog>> = throw NotImplementedError()

    companion object {
        val currentDate: String
            get() = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"))
                .format(FakeDateTimeProviderModule.instant)

        fun subscription(
            id: String,
            channel: YouTubeChannel,
        ): YouTubeSubscription = object : YouTubeSubscription {
            override val id: YouTubeSubscription.Id = YouTubeSubscription.Id(id)
            override val channel: YouTubeChannel = channel
            override val subscribeSince: Instant = Instant.EPOCH
        }

        fun channelDetail(
            id: Int,
        ): YouTubeChannelDetail = object : YouTubeChannelDetail {
            override val id: YouTubeChannel.Id = YouTubeChannel.Id("channel_$id")
            override val uploadedPlayList: YouTubePlaylist.Id =
                YouTubePlaylist.Id("playlist_${this.id.value}")
            override val title: String = "channel$id"
            override val iconUrl: String = "<url is here>"
            override val bannerUrl: String = ""
            override val subscriberCount: BigInteger = BigInteger.ONE
            override val isSubscriberHidden: Boolean = false
            override val videoCount: BigInteger = BigInteger.ONE
            override val viewsCount: BigInteger = BigInteger.ONE
            override val publishedAt: Instant = Instant.EPOCH
            override val customUrl: String = ""
            override val keywords: Collection<String> = emptyList()
            override val description: String = ""
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
                        videoJson(items.item)
                    }

                    "/youtube/v3/channels" -> {
                        val id = requireNotNull(request.url.queryParameterValues("id"))
                            .mapNotNull { i -> i?.let { YouTubeChannel.Id(it) } }.toSet()
                        when (val part = request.url.queryParameterValues("part").toSet()) {
                            setOf("snippet") -> {
                                val res = client.fetchChannelList(id)
                                channelJson(res.item, hasSnippet = true)
                            }

                            setOf("contentDetails") -> {
                                val res = client.fetchChannelRelatedPlaylistList(id)
                                channelJson(res.item, hasContentDetail = true)
                            }

                            else -> error("unexpected part: $part")
                        }
                    }

                    "/youtube/v3/playlists" -> {
                        val id = requireNotNull(request.url.queryParameter("id"))
                        val res = client.fetchPlaylist(setOf(YouTubePlaylist.Id(id)))
                        playlistJson(res.item)
                    }

                    "/youtube/v3/playlistItems" -> {
                        val id = requireNotNull(request.url.queryParameter("playlistId"))
                        val maxResult = requireNotNull(request.url.queryParameter("maxResults")).toLong()
                        val eTag = request.headers["If-None-Match"]
                        val res = client.fetchPlaylistItems(YouTubePlaylist.Id(id), maxResult, eTag)
                        playlistItemJson(res.item)
                    }

                    "/youtube/v3/subscriptions" -> {
                        check(request.url.queryParameter("mine").toBoolean())
                        val eTag = request.headers["If-None-Match"]
                        val pageToken = request.url.queryParameter("pageToken")
                        val order = request.url.queryParameter("order")
                        val res = client.fetchSubscription(
                            object : YouTubeSubscriptionQuery {
                                override val offset: Int get() = error("unused value")
                                override val nextPageToken: String? get() = pageToken
                                override val eTag: String? get() = eTag
                                override val order: YouTubeSubscriptionQuery.Order
                                    get() = when (order) {
                                        "relevance" -> YouTubeSubscriptionQuery.Order.RELEVANCE
                                        "alphabetical" -> YouTubeSubscriptionQuery.Order.ALPHABETICAL
                                        else -> error("unexpected order: $order")
                                    }
                            },
                        )
                        subscriptionJson(res)
                    }

                    else -> throw AssertionError("unexpected path: $path")
                }
            }.fold(
                onSuccess = {
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("date", currentDate)
                        .body(it)
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

    override fun finished(description: Description) {
        server.closeQuietly()
    }
}

private fun youtubeResponseJson(
    kind: String,
    etag: String = "etag",
    nextPageToken: String? = null,
    prevPageToken: String? = null,
    totalResults: Int = 1,
    items: List<Json>,
): String = Json.Obj(
    "kind" to kind,
    "etag" to etag,
    "nextPageToken" to nextPageToken,
    "prevPageToken" to prevPageToken,
    "pageInfo" to Json.Obj(
        "totalResults" to totalResults,
        "resultsPerPage" to items.size,
    ),
    "items" to Json.Arr(items),
).toString()

private fun videoJson(items: List<YouTubeVideo>): String = youtubeResponseJson(
    kind = "youtube#videoListResponse",
    etag = "etag",
    items = items.map { i ->
        Json.Obj(
            "kind" to "youtube#video",
            "etag" to "etag",
            "id" to i.id.value,
            "snippet" to Json.Obj(
                "publishedAt" to null,
                "channelId" to i.channel.id.value,
                "title" to "title${i.id.value}",
                "description" to "description",
                "thumbnails" to Json.Obj(
                    "standard" to Json.Obj("url" to "<url is here>", "width" to 480, "height" to 360),
                ),
                "channelTitle" to i.channel.title,
                "tags" to Json.Arr(emptyList()),
                "categoryId" to "categoryid${i.id.value}",
                "liveBroadcastContent" to i.liveBroadcastContent.name.lowercase(),
                "defaultLanguage" to "ja",
                "localized" to Json.Obj(
                    "title" to "title${i.id.value}",
                    "description" to "description",
                ),
                "defaultAudioLanguage" to "ja",
            ),
            "liveStreamingDetails" to Json.Obj(
                "actualStartTime" to i.actualStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "actualEndTime" to i.actualEndDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "scheduledStartTime" to i.scheduledStartDateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                "scheduledEndTime" to null,
                "concurrentViewers" to "100",
                "activeLiveChatId" to "livechatid${i.id.value}",
            ),
        )
    },
)

private fun channelJson(
    items: List<YouTubeChannelBase>,
    hasSnippet: Boolean = false,
    hasContentDetail: Boolean = false,
): String {
    val snippet: ((YouTubeChannelBase) -> Json?) = {
        if (hasSnippet) {
            val i = it as YouTubeChannel
            Json.Obj(
                "title" to i.title,
                "description" to "description",
                "customUrl" to "",
                "publishedAt" to "1970-01-01T00:00:00Z",
                "thumbnails" to Json.Obj(
                    "medium" to Json.Obj(
                        "url" to "<url is here>",
                        "width" to 240,
                        "height" to 240,
                    ),
                ),
                "defaultLanguage" to "ja",
                "localized" to Json.Obj(
                    "title" to i.title,
                    "description" to "description",
                ),
                "country" to "jp",
            )
        } else {
            null
        }
    }
    val contentDetail: ((YouTubeChannelBase) -> Json?) = {
        if (hasContentDetail) {
            Json.Obj(
                "relatedPlaylists" to Json.Obj(
                    "likes" to "",
                    "favorites" to "",
                    "uploads" to (it as YouTubeChannelRelatedPlaylist).uploadedPlayList!!.value,
                ),
            )
        } else {
            null
        }
    }
    return youtubeResponseJson(
        kind = "youtube#channelListResponse",
        etag = "etag",
        items = items.map { i ->
            Json.Obj(
                "kind" to "youtube#channel",
                "etag" to "etag",
                "id" to i.id.value,
                "snippet" to snippet(i),
                "contentDetails" to contentDetail(i),
            )
        },
    )
}

private fun playlistJson(items: List<YouTubePlaylist>): String = youtubeResponseJson(
    kind = "youtube#playlistListResponse",
    items = items.map {
        Json.Obj(
            "kind" to "youtube#playlist",
            "etag" to "etag",
            "id" to it.id.value,
            "snippet" to Json.Obj(
                "publishedAt" to "1970-01-01T00:00:00Z",
                "channelId" to "channel_id",
                "title" to it.title,
                "description" to "description",
                "thumbnails" to Json.Obj(
                    "standard" to Json.Obj(
                        "url" to "<url is here>",
                        "width" to 480,
                        "height" to 320,
                    ),
                ),
                "channelTitle" to "channeltitle",
                "defaultLanguage" to "ja",
                "localized" to Json.Obj(
                    "title" to it.title,
                    "description" to "description",
                ),
            ),
            "contentDetails" to Json.Obj(
                "itemCount" to 10,
            ),
        )
    },
)

private fun playlistItemJson(items: List<YouTubePlaylistItem>): String = youtubeResponseJson(
    kind = "youtube#playlistItemListResponse",
    items = items.map {
        Json.Obj(
            "kind" to "youtube#playlistItem",
            "etag" to "etag",
            "id" to it.id.value,
            "contentDetails" to Json.Obj(
                "videoId" to it.videoId.value,
                "startAt" to "",
                "endAt" to "",
                "note" to "",
                "videoPublishedAt" to "${it.publishedAt}",
            ),
        )
    },
)

private fun subscriptionJson(res: NetworkResponse<List<YouTubeSubscription>>): String = youtubeResponseJson(
    kind = "youtube#subscriptionListResponse",
    etag = res.eTag ?: "etag",
    nextPageToken = res.nextPageToken,
    items = res.item.map {
        Json.Obj(
            "kind" to "youtube#subscription",
            "etag" to "etag",
            "id" to it.id.value,
            "snippet" to Json.Obj(
                "publishedAt" to "1970-01-01T00:00:00Z",
                "channelTitle" to it.channel.title,
                "title" to "title_${it.channel.title}",
                "description" to "description",
                "resourceId" to Json.Obj(
                    "kind" to "string",
                    "channelId" to it.channel.id.value,
                ),
                "channelId" to it.channel.id.value,
                "thumbnails" to Json.Obj(
                    "medium" to Json.Obj(
                        "url" to "<url is here>",
                        "width" to 240,
                        "height" to 240,
                    ),
                ),
            ),
        )
    },
)

sealed class Json {
    class Obj(val map: Map<String, Json>) : Json() {
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

        override fun toString(): String = "{" + map.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}"
    }

    class Arr(val list: List<Json>) : Json() {
        override fun toString(): String = "[" + list.joinToString(",") + "]"
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
