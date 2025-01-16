package com.freshdigitable.yttt.data.model

import java.time.Duration
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
    val channel: YouTubeChannel
    val thumbnailUrl: String
    val videoId: YouTubeVideo.Id
    val description: String
    val videoOwnerChannelId: YouTubeChannel.Id?
    val publishedAt: Instant

    data class Id(override val value: String) : YouTubeId
}

interface YouTubePlaylistWithItems : Updatable {
    val playlist: YouTubePlaylist
    val items: List<YouTubePlaylistItem>
}

interface Updatable {
    val maxAge: Duration
    val fetchedAt: Instant

    companion object {
        private val Updatable.updatableAt: Instant get() = fetchedAt + maxAge
        fun Updatable.isUpdatable(current: Instant): Boolean = updatableAt <= current
    }
}

data class YouTubePlaylistItemEntity(
    override val id: YouTubePlaylistItem.Id,
    override val playlistId: YouTubePlaylist.Id,
    override val title: String,
    override val channel: YouTubeChannel,
    override val thumbnailUrl: String,
    override val videoId: YouTubeVideo.Id,
    override val description: String,
    override val videoOwnerChannelId: YouTubeChannel.Id?,
    override val publishedAt: Instant,
) : YouTubePlaylistItem
