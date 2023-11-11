package com.freshdigitable.yttt.data.model

interface TwitchId : IdBase<String> {
    override val platform: LivePlatform get() = LivePlatform.TWITCH
}
