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
import com.freshdigitable.yttt.di.GoogleAccountModule
import com.freshdigitable.yttt.test.FakeYouTubeClient.Companion.PATH_SUBSCRIPTION
import com.freshdigitable.yttt.test.Json.Companion.obj
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

fun MockServerRule.setClient(
    videoList: ((Set<YouTubeVideo.Id>) -> List<VideoJson>)? = null,
    channelList: ((Pair<Set<YouTubeChannel.Id>, Set<String>>) -> List<ChannelItemJson>)? = null,
    channelListRes: ((Pair<Set<YouTubeChannel.Id>, Set<String>>) -> ResponseJson)? = null,
    playlist: ((Set<YouTubePlaylist.Id>) -> List<PlaylistJson>)? = null,
    playlistItems: ((Pair<YouTubePlaylist.Id, String?>) -> List<PlaylistItemJson>)? = null,
    playlistItemsRes: ((Pair<YouTubePlaylist.Id, String?>) -> ResponseJson)? = null,
    subscription: ((String?, YouTubeSubscriptionQuery.Order) -> ResponseJson)? = null,
) {
    val client = object : FakeYouTubeClient {
        override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): ResponseJson = videoList!!(ids).responseJson()
        override fun fetchChannels(ids: Set<YouTubeChannel.Id>, part: Set<String>): ResponseJson =
            channelList?.invoke(ids to part)?.responseJson() ?: channelListRes!!(ids to part)

        override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<PlaylistJson> = playlist!!(ids)
        override fun fetchPlaylistItems(
            id: YouTubePlaylist.Id,
            maxResult: Long,
            eTag: String?,
        ): ResponseJson = playlistItems?.invoke(id to eTag)?.responseJson() ?: playlistItemsRes!!(id to eTag)

        override fun fetchSubscription(
            nextPageToken: String?,
            order: YouTubeSubscriptionQuery.Order,
        ): ResponseJson = subscription!!(nextPageToken, order)
    }
    setClient(client)
}

fun MockServerRule.setClient(client: FakeYouTubeClient) {
    val dispatcher = object : TestDispatcher {
        override fun dispatch(request: TestDispatcher.Request): ResponseJson {
            return when (val path = request.encodedPath) {
                "/youtube/v3/videos" -> {
                    val id = request.queryParams("id")
                        .mapNotNull { i -> i?.let { YouTubeVideo.Id(it) } }
                    client.fetchVideoList(id.toSet())
                }

                "/youtube/v3/channels" -> {
                    val id = requireNotNull(request.queryParams("id"))
                        .mapNotNull { i -> i?.let { YouTubeChannel.Id(it) } }.toSet()
                    val part = request.queryParams("part").filterNotNull().toSet()
                    client.fetchChannels(id, part)
                }

                "/youtube/v3/playlists" -> {
                    val id = requireNotNull(request.queryParam("id"))
                    client.fetchPlaylist(setOf(YouTubePlaylist.Id(id))).responseJson()
                }

                "/youtube/v3/playlistItems" -> {
                    val id = requireNotNull(request.queryParam("playlistId"))
                    val maxResult = requireNotNull(request.queryParam("maxResults")).toLong()
                    val eTag = request.header("If-None-Match")
                    val res = client.fetchPlaylistItems(YouTubePlaylist.Id(id), maxResult, eTag)
                    if (res is YouTubeResponseJson && res.eTag == eTag) {
                        YouTubeErrorJson.notModified
                    } else {
                        res
                    }
                }

                PATH_SUBSCRIPTION -> {
                    check(request.queryParam("mine").toBoolean())
                    val eTag = request.header("If-None-Match")
                    val pageToken = request.queryParam("pageToken")
                    val order = requireNotNull(request.queryParam("order"))
                    val o = YouTubeSubscriptionQuery.Order.valueOf(order.uppercase())
                    val res = client.fetchSubscription(nextPageToken = pageToken, order = o)
                    if (res is YouTubeResponseJson && res.eTag == eTag) {
                        YouTubeErrorJson.notModified
                    } else {
                        res
                    }
                }

                else -> throw AssertionError("unexpected path: $path")
            }
        }
    }
    setClient(dispatcher)
}

interface FakeYouTubeClient {
    companion object {
        const val PATH_SUBSCRIPTION = "/youtube/v3/subscriptions"
    }

    fun fetchSubscription(nextPageToken: String? = null, order: YouTubeSubscriptionQuery.Order): ResponseJson =
        throw NotImplementedError()

    fun fetchChannels(ids: Set<YouTubeChannel.Id>, part: Set<String>): ResponseJson = throw NotImplementedError()

    fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): List<PlaylistJson> = throw NotImplementedError()

    fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): ResponseJson = throw NotImplementedError()

    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): ResponseJson = throw NotImplementedError()
}

fun CacheControl.Companion.fromRemote(fetchedAt: Instant): CacheControl =
    CacheControl.create(fetchedAt, MAX_AGE_DEFAULT)

inline fun <reified T : Json> List<T>.responseJson(
    eTag: String? = null,
    pageToken: String? = null,
): YouTubeResponseJson {
    val kind = when (T::class) {
        VideoJson::class -> "youtube#videoListResponse"
        ChannelItemJson::class -> "youtube#channelListResponse"
        PlaylistJson::class -> "youtube#playlistListResponse"
        PlaylistItemJson::class -> "youtube#playlistItemListResponse"
        SubscriptionItemJson::class -> "youtube#subscriptionListResponse"
        else -> error("unsupported: ${T::class}")
    }
    return YouTubeResponseJson(kind = kind, eTag = eTag ?: "eTag", nextPageToken = pageToken, items = this)
}

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
                this["uploads"] = playlistId ?: "playlist_${id.value}"
            }
        }

    override fun toString(): String = json.toString()
}

class PlaylistJson(val id: String, val title: String = "playlist_$id") : Json {
    private val json: Json
        get() = obj {
            this["kind"] = "youtube#playlist"
            this["etag"] = "etag"
            this["id"] = id
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

class PlaylistItemJson(
    val id: String,
    val playlistId: String,
    val videoId: String = "video_${id}_$playlistId",
) : Json {
    companion object {
        fun List<PlaylistItemJson>.eTag(): String = joinToString(",") { it.key }.sha1()
    }

    val key: String = listOf(id, playlistId, videoId).joinToString { it }
    private val json: String = obj {
        this["kind"] = "youtube#playlistItem"
        this["etag"] = "etag"
        this["id"] = id
        this["contentDetails"] = obj {
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
