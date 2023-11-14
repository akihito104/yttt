package com.freshdigitable.yttt.data.model

import kotlin.reflect.KClass

interface IdBase {
    val value: String
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

@Deprecated("implement ID class for each platform")
enum class LivePlatform {
    YOUTUBE, TWITCH,
}

interface LiveId : IdBase {
    val type: KClass<*>
}
