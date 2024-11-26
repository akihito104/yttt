package com.freshdigitable.yttt.data.source.local.db

import androidx.room.TypeConverter
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

internal abstract class Converter<S, O>(
    private val serialize: (O) -> S,
    private val createObject: (S) -> O,
) {
    @TypeConverter
    fun toSerial(value: O?): S? = value?.let { serialize(it) }

    @TypeConverter
    fun toObject(value: S?): O? = value?.let { createObject(it) }
}

internal class InstantConverter : Converter<Long, Instant>(
    serialize = { it.toEpochMilli() },
    createObject = { Instant.ofEpochMilli(it) },
)

internal class DurationConverter : Converter<Long, Duration>(
    serialize = { it.toMillis() },
    createObject = { Duration.ofMillis(it) }
)

internal abstract class IdConverter<E : IdBase>(createObject: (String) -> E) :
    Converter<String, E>(serialize = { it.value }, createObject = createObject)

internal class YouTubeChannelIdConverter :
    IdConverter<YouTubeChannel.Id>(createObject = { YouTubeChannel.Id(it) })

internal class YouTubeSubscriptionIdConverter : IdConverter<YouTubeSubscription.Id>(
    createObject = { YouTubeSubscription.Id(it) }
)

internal class YouTubeVideoIdConverter :
    IdConverter<YouTubeVideo.Id>(createObject = { YouTubeVideo.Id(it) })

internal class YouTubeChannelLogIdConverter :
    IdConverter<YouTubeChannelLog.Id>(createObject = { YouTubeChannelLog.Id(it) })

internal class YouTubePlaylistIdConverter :
    IdConverter<YouTubePlaylist.Id>(createObject = { YouTubePlaylist.Id(it) })

internal class YouTubePlaylistItemIdConverter :
    IdConverter<YouTubePlaylistItem.Id>(createObject = { YouTubePlaylistItem.Id(it) })

internal class BigIntegerConverter : Converter<Long, BigInteger>(
    serialize = { it.toLong() },
    createObject = { BigInteger.valueOf(it) }
)

internal class YouTubeVideoBroadcastType : Converter<String?, YouTubeVideo.BroadcastType?>(
    serialize = YouTubeVideoBroadcastType::serialize,
    createObject = YouTubeVideoBroadcastType::createObject,
) {
    companion object {
        private val TABLE = mapOf(
            YouTubeVideo.BroadcastType.LIVE to "live",
            YouTubeVideo.BroadcastType.UPCOMING to "upcoming",
            YouTubeVideo.BroadcastType.NONE to "none",
        )

        private fun serialize(value: YouTubeVideo.BroadcastType?): String? = if (value == null) {
            null
        } else {
            TABLE[value] ?: throw NotImplementedError("unknown type: $value")
        }

        private fun createObject(value: String?): YouTubeVideo.BroadcastType? = if (value == null) {
            null
        } else {
            TABLE.entries.firstOrNull { it.value == value }?.key
                ?: throw NotImplementedError("unknown type: $value")
        }
    }
}
