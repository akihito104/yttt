package com.freshdigitable.yttt.data.model

import java.time.Instant

interface YouTubeSubscriptionSummary {
    val subscriptionId: YouTubeSubscription.Id
    val channelId: YouTubeChannel.Id
    val uploadedPlaylistId: YouTubePlaylist.Id?
    val playlistExpiredAt: Instant?
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        fun YouTubeSubscriptionSummary.needsUpdatePlaylist(current: Instant): Boolean {
            val e = playlistExpiredAt ?: return true
            return e < current
        }
    }
}

interface YouTubePlaylistItemSummary {
    val playlistId: YouTubePlaylist.Id
    val playlistItemId: YouTubePlaylistItem.Id
    val videoId: YouTubeVideo.Id
    val isArchived: Boolean?
}
