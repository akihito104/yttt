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
