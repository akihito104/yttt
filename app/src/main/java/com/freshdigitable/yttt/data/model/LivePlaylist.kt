package com.freshdigitable.yttt.data.model

import java.time.Instant

interface LivePlaylist {
    val id: Id
    val title: String
    val thumbnailUrl: String

    data class Id(override val value: String) : IdBase<String> {
        override val platform: LivePlatform = LivePlatform.YOUTUBE
    }
}

interface LivePlaylistItem {
    val id: Id
    val playlistId: LivePlaylist.Id
    val title: String
    val channel: LiveChannel
    val thumbnailUrl: String
    val videoId: LiveVideo.Id
    val description: String
    val videoOwnerChannelId: LiveChannel.Id?
    val publishedAt: Instant

    data class Id(override val value: String) : IdBase<String> {
        override val platform: LivePlatform = LivePlatform.YOUTUBE
    }
}

data class LivePlaylistItemEntity(
    override val id: LivePlaylistItem.Id,
    override val playlistId: LivePlaylist.Id,
    override val title: String,
    override val channel: LiveChannel,
    override val thumbnailUrl: String,
    override val videoId: LiveVideo.Id,
    override val description: String,
    override val videoOwnerChannelId: LiveChannel.Id?,
    override val publishedAt: Instant,
) : LivePlaylistItem
