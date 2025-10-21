package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelBase
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClientImpl.Companion.fetch
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.ChannelListResponse
import java.math.BigInteger
import java.time.Instant

interface YouTubeChannelClient {
    fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannel>>
    fun fetchChannelDetailList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>>
    fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): NetworkResponse<List<YouTubeChannelRelatedPlaylist>>
}

internal class YouTubeChannelClientImpl(private val youtube: YouTube) : YouTubeChannelClient {
    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannel>> =
        youtube.fetchChannelList(ids, YouTubeChannelRemote.factory, YouTubeChannelRemote.part)

    override fun fetchChannelDetailList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
        youtube.fetchChannelList(ids, YouTubeChannelDetailImpl.factory, YouTubeChannelDetailImpl.part)

    override fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): NetworkResponse<List<YouTubeChannelRelatedPlaylist>> = youtube.fetchChannelList(
        ids,
        YouTubeChannelRelatedPlaylistRemote.factory,
        YouTubeChannelRelatedPlaylistRemote.part,
    )

    private fun <T : YouTubeChannelBase> YouTube.fetchChannelList(
        ids: Set<YouTubeChannel.Id>,
        factory: ResponseFactory<ChannelListResponse, List<T>>,
        part: List<String>,
    ): NetworkResponse<List<T>> = fetch(factory) {
        channels()
            .list(part)
            .setId(ids.map { it.value })
            .setMaxResults(ids.size.toLong())
    }
}

private class YouTubeChannelRemote(
    private val channel: Channel,
) : YouTubeChannel {
    override val id: YouTubeChannel.Id get() = YouTubeChannel.Id(channel.id)
    override val title: String get() = channel.snippet.title
    override val iconUrl: String get() = channel.snippet.thumbnails.iconUrl

    companion object {
        val factory: ResponseFactory<ChannelListResponse, List<YouTubeChannel>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeChannelRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
        val part = listOf(YouTubeClient.PART_SNIPPET)
    }
}

private class YouTubeChannelRelatedPlaylistRemote(
    private val channel: Channel,
) : YouTubeChannelRelatedPlaylist {
    override val id: YouTubeChannel.Id get() = YouTubeChannel.Id(channel.id)
    override val uploadedPlayList: YouTubePlaylist.Id?
        get() = channel.contentDetails?.relatedPlaylists?.uploads?.let { YouTubePlaylist.Id(it) }

    companion object {
        val factory: ResponseFactory<ChannelListResponse, List<YouTubeChannelRelatedPlaylist>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeChannelRelatedPlaylistRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
        val part = listOf(YouTubeClient.PART_CONTENT_DETAILS)
    }
}

private data class YouTubeChannelDetailImpl(
    private val channel: Channel,
) : YouTubeChannelDetail,
    YouTubeChannel by YouTubeChannelRemote(channel),
    YouTubeChannelRelatedPlaylist by YouTubeChannelRelatedPlaylistRemote(channel) {
    override val id: YouTubeChannel.Id get() = YouTubeChannel.Id(channel.id)
    override val customUrl: String get() = channel.snippet.customUrl ?: ""
    override val publishedAt: Instant get() = channel.snippet.publishedAt.toInstant()

    override val bannerUrl: String? get() = channel.brandingSettings?.image?.bannerExternalUrl
    override val keywords: Collection<String>
        get() = channel.brandingSettings?.channel?.keywords?.split(",", " ") ?: emptyList()
    override val description: String? get() = channel.brandingSettings?.channel?.description

    override val subscriberCount: BigInteger get() = channel.statistics.subscriberCount
    override val isSubscriberHidden: Boolean get() = channel.statistics.hiddenSubscriberCount
    override val videoCount: BigInteger get() = channel.statistics.videoCount
    override val viewsCount: BigInteger get() = channel.statistics.viewCount ?: BigInteger.ZERO

    override fun toString(): String = channel.toPrettyString()

    companion object {
        val factory: ResponseFactory<ChannelListResponse, List<YouTubeChannelDetail>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubeChannelDetailImpl(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
        val part = listOf(
            YouTubeClient.PART_SNIPPET,
            YouTubeClient.PART_CONTENT_DETAILS,
            "brandingSettings",
            "statistics",
        )
    }
}
