package com.freshdigitable.yttt.data.model

interface IdBase<S> {
    val value: S
    val platform: LivePlatform get() = LivePlatform.YOUTUBE
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

enum class LivePlatform {
    YOUTUBE, TWITCH,
}
