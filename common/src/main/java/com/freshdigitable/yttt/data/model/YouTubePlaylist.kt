package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubePlaylist {
    val id: Id
    val title: String
    val thumbnailUrl: String

    @JvmInline
    value class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistItem {
    val id: Id
    val playlistId: YouTubePlaylist.Id
    val videoId: YouTubeVideo.Id
    val publishedAt: Instant

    @JvmInline
    value class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistItemDetail : YouTubePlaylistItem {
    val title: String
    val channel: YouTubeChannelTitle
    val thumbnailUrl: String
    val description: String
    val videoOwnerChannelId: YouTubeChannel.Id?

    companion object {
        val YouTubePlaylistItemDetail.isFromAnotherChannel: Boolean get() = channel.id != videoOwnerChannelId
    }
}
