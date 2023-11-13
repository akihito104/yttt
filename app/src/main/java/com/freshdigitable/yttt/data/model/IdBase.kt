package com.freshdigitable.yttt.data.model

import kotlin.reflect.KClass

interface IdBase {
    val value: String

    @Deprecated("implement ID class for each platform")
    val platform: LivePlatform
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

@Deprecated("implement ID class for each platform")
enum class LivePlatform {
    YOUTUBE, TWITCH,
}

interface LiveId : IdBase {
    val type: KClass<*>
    override val platform: LivePlatform
        get() = when (type) {
            YouTubeVideo.Id::class, YouTubeSubscription.Id::class,
            YouTubeChannel.Id::class, YouTubeChannelLog.Id::class,
            YouTubePlaylist::class, YouTubePlaylistItem::class -> LivePlatform.YOUTUBE

            TwitchVideo.Id::class, TwitchUser.Id::class, TwitchStream.Id::class,
            TwitchChannelSchedule.Stream.Id::class -> LivePlatform.TWITCH

            else -> throw AssertionError("unsupported type: $type")
        }
}
