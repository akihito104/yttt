package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
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
import com.freshdigitable.yttt.di.YouTubeModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.math.BigInteger
import java.time.Instant
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [YouTubeModule::class],
)
interface FakeYouTubeClientModule {
    companion object {
        var client: YouTubeClient = object : FakeYouTubeClient() {}

        @Singleton
        @Provides
        fun provideClient(): YouTubeClient = client

        @Singleton
        @Provides
        fun provideNewChooseAccountIntentProvider(): NewChooseAccountIntentProvider =
            object : NewChooseAccountIntentProvider {
                override fun invoke(): Intent = throw NotImplementedError()
            }

        fun clean() {
            client = object : FakeYouTubeClient() {}
        }
    }
}

abstract class FakeYouTubeClient : YouTubeClient {
    override fun fetchSubscription(query: YouTubeSubscriptionQuery): NetworkResponse<List<YouTubeSubscription>> =
        throw NotImplementedError()

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
        throw NotImplementedError()

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
