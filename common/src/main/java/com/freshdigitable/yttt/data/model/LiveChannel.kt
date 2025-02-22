package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.reflect.KClass

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String
    val platform: LivePlatform

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
    override val platform: LivePlatform,
) : LiveChannel

interface LiveChannelDetailBody : LiveChannel {
    val bannerUrl: String?
    val statsText: String

    companion object {
        val BigInteger.toStringWithComma: String
            get() = NumberFormat.getNumberInstance(Locale.US).format(this)
        const val STATS_SEPARATOR: String = "・"
    }
}

data class AnnotatedLiveChannelDetail(
    private val detail: LiveChannelDetailBody,
    val annotatedDescription: AnnotatableString,
) : LiveChannelDetailBody by detail
