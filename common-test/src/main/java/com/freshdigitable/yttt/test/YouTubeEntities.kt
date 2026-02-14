package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.math.BigInteger
import java.time.Instant

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
