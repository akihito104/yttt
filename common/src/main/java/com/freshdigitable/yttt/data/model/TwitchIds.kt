package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase

object Twitch : LivePlatform {
    override val name: String = "Twitch"
    override val color: Long = 0xFF9146FF
}
