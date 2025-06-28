package com.freshdigitable.yttt.test

import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.data.source.remote.YouTubeClient.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.di.YouTubeModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.math.BigInteger
import java.time.Duration
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
    override fun fetchSubscription(
        pageSize: Long,
        offset: Int,
        token: String?
    ): NetworkResponse<List<YouTubeSubscription>> = throw NotImplementedError()

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<Updatable<YouTubeChannelDetail>>> =
        throw NotImplementedError()

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<Updatable<YouTubePlaylist>>> =
        throw NotImplementedError()

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<Updatable<List<YouTubePlaylistItem>>> = throw NotImplementedError()

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<Updatable<YouTubeVideo>>> =
        throw NotImplementedError()

    override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> =
        throw NotImplementedError()

    override fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
        token: String?
    ): NetworkResponse<List<YouTubeChannelLog>> = throw NotImplementedError()

    companion object {
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

        fun <T> T.updatable(
            fetchedAt: Instant? = Instant.EPOCH,
            maxAge: Duration? = MAX_AGE_DEFAULT,
        ): Updatable<T> = Updatable.create(this, CacheControl.create(fetchedAt, maxAge))
    }
}
