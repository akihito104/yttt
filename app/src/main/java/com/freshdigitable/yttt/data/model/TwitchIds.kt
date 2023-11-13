package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase {
    override val platform: LivePlatform get() = LivePlatform.TWITCH
}
