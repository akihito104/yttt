package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase

object Twitch : LivePlatform {
    override val name: String = "Twitch"
    override val color: Long = BRAND_COLOR

    // https://brand.twitch.com/
    private const val BRAND_COLOR = 0xFF9146FF
}

inline fun <reified T : IdBase> IdBase.toTwitchId(): T {
    (this as? LiveId)?.checkMappable<T>()
    return when (T::class) {
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class -> TwitchChannelSchedule.Stream.Id(value) as T
        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T
        else -> throw AssertionError("unsupported id type: $this")
    }
}
