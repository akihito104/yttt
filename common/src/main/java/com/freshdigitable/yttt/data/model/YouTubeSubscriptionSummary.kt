package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.isUpdatable
import kotlin.time.Instant

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
