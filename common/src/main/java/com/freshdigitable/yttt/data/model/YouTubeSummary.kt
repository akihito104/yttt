package com.freshdigitable.yttt.data.model

interface YouTubeSubscriptionSummary {
    val subscriptionId: YouTubeSubscription.Id
    val channelId: YouTubeChannel.Id
    val uploadedPlaylistId: YouTubePlaylist.Id?
    val cacheControl: CacheControl
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object
}

interface YouTubePlaylistItemSummary {
    val playlistId: YouTubePlaylist.Id
    val playlistItemId: YouTubePlaylistItem.Id
    val videoId: YouTubeVideo.Id
    val isArchived: Boolean?
    val videoCacheControl: CacheControl?
}
