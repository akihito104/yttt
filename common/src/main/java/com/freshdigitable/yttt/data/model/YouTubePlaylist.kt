package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubePlaylist {
    val id: Id
    val title: String
    val thumbnailUrl: String

    data class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistItemIds {
    val id: YouTubePlaylistItem.Id
    val playlistId: YouTubePlaylist.Id
    val videoId: YouTubeVideo.Id
}

interface YouTubePlaylistItem : YouTubePlaylistItemIds {
    val title: String
    val channel: YouTubeChannelTitle
    val thumbnailUrl: String
    val description: String
    val videoOwnerChannelId: YouTubeChannel.Id?
    val publishedAt: Instant

    data class Id(override val value: String) : YouTubeId

    companion object {
        val YouTubePlaylistItem.isFromAnotherChannel: Boolean get() = channel.id != videoOwnerChannelId
    }
}
