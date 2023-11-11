package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase<String> {
    override val platform: LivePlatform get() = LivePlatform.TWITCH
}

inline fun <reified T : IdBase<String>> IdBase<String>.mapTo(): T {
    return when (T::class) {
        YouTubeVideo.Id::class -> YouTubeVideo.Id(value) as T
        YouTubeChannel.Id::class -> YouTubeChannel.Id(value) as T
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class -> TwitchChannelSchedule.Stream.Id(value) as T
        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T
        LiveSubscription.Id::class -> LiveSubscription.Id(value, platform) as T
        LiveVideo.Id::class -> LiveVideo.Id(value, platform) as T
        LiveChannel.Id::class -> LiveChannel.Id(value, platform) as T
        else -> throw AssertionError("unsupported id type: $this")
    }
}
