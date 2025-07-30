package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.isUpdatable
import java.time.Instant

interface YouTubeSubscriptionSummary {
    val subscriptionId: YouTubeSubscription.Id
    val channelId: YouTubeChannel.Id
    val uploadedPlaylistId: YouTubePlaylist.Id?
    val cacheControl: CacheControl
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        fun YouTubeSubscriptionSummary.isPlaylistItemUpdatable(current: Instant): Boolean =
            cacheControl.isUpdatable(current)
    }
}
