package com.freshdigitable.yttt.data.model

interface LiveChannel {
    val id: Id
    val title: String

    data class Id(val value: String)
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
) : LiveChannel
