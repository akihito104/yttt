package com.freshdigitable.yttt.data.model

interface LivePlaylist {
    val id: Id

    data class Id(override val value: String) : IdBase<String>
}

interface LivePlaylistItem {
    val id: Id
    val playlistId: LivePlaylist.Id

    data class Id(override val value: String) : IdBase<String>
}

data class LivePlaylistItemEntity(
    override val id: LivePlaylistItem.Id,
    override val playlistId: LivePlaylist.Id,
) : LivePlaylistItem
