package com.freshdigitable.yttt.data.model

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(override val value: String) : IdBase<String>
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
) : LiveChannel

interface IdBase<S> {
    val value: S
}
