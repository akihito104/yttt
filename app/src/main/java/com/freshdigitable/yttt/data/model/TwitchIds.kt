package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase<String> {
    override val platform: LivePlatform get() = LivePlatform.TWITCH
}

inline fun <reified T : TwitchId> IdBase<String>.mapTo(): T {
    check(this.platform == LivePlatform.TWITCH)
    return when (T::class) {
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class ->
            TwitchChannelSchedule.Stream.Id(value) as T

        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T

        else -> throw AssertionError("unsupported id type: $this")
    }
}
