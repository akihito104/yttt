package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase

object Twitch : LivePlatform {
    override val name: String = "Twitch"
}
