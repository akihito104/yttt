package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase

object Twitch : LivePlatform {
    override val name: String = "Twitch"
    override val color: Long = BRAND_COLOR

    // https://brand.twitch.com/
    private const val BRAND_COLOR = 0xFF9146FF
}
