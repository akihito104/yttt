package com.freshdigitable.yttt.data.model

inline fun <reified T : IdBase> IdBase.toLiveId(): T {
    (this as? LiveId)?.checkMappable<T>()
    return when (T::class) {
        LiveSubscription.Id::class -> LiveSubscription.Id(value, this::class) as T
        LiveVideo.Id::class -> LiveVideo.Id(value, this::class) as T
        LiveChannel.Id::class -> LiveChannel.Id(value, this::class) as T
        else -> throw AssertionError("unsupported id type: $this")
    }
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

inline fun <reified T : IdBase> IdBase.checkMappable() {
    if (this is LiveId) {
        check(this.type == T::class) { "unmappable: ${this.type} to ${T::class}" }
    }
}
