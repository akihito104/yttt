package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubePlaylist {
    val id: Id
    val title: String
    val thumbnailUrl: String

    data class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistUpdatable : YouTubePlaylist, Updatable

interface YouTubePlaylistItem {
    val id: Id
    val playlistId: YouTubePlaylist.Id
    val title: String
    val channel: YouTubeChannelTitle
    val thumbnailUrl: String
    val videoId: YouTubeVideo.Id
    val description: String
    val videoOwnerChannelId: YouTubeChannel.Id?
    val publishedAt: Instant

    data class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistItemUpdatable : YouTubePlaylistItem, Updatable
