package com.freshdigitable.yttt.data.model

interface LivePlaylist {
    val id: Id

    data class Id(override val value: String) : IdBase<String>
}

interface LivePlaylistItem {
    val id: Id
    val playlistId: LivePlaylist.Id
    val title: String
    val channel: LiveChannel
    val thumbnailUrl: String
    val videoId: LiveVideo.Id
    val description: String

    data class Id(override val value: String) : IdBase<String>
}

data class LivePlaylistItemEntity(
    override val id: LivePlaylistItem.Id,
    override val playlistId: LivePlaylist.Id,
    override val title: String,
    override val channel: LiveChannel,
    override val thumbnailUrl: String,
    override val videoId: LiveVideo.Id,
    override val description: String,
) : LivePlaylistItem
