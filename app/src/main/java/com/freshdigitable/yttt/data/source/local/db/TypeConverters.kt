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

abstract class Converter<S, O>(
    private val serialize: (O) -> S,
    private val createObject: (S) -> O,
) {
    @TypeConverter
    fun toSerial(value: O?): S? = value?.let { serialize(it) }

    @TypeConverter
    fun toObject(value: S?): O? = value?.let { createObject(it) }
}

class InstantConverter : Converter<Long, Instant>(
    serialize = { it.toEpochMilli() },
    createObject = { Instant.ofEpochMilli(it) },
)

class DurationConverter : Converter<Long, Duration>(
    serialize = { it.toMillis() },
    createObject = { Duration.ofMillis(it) }
)

abstract class IdConverter<E : IdBase<String>>(createObject: (String) -> E) :
    Converter<String, E>(serialize = { it.value }, createObject = createObject)

class YouTubeChannelIdConverter :
    IdConverter<YouTubeChannel.Id>(createObject = { YouTubeChannel.Id(it) })

class YouTubeSubscriptionIdConverter : IdConverter<YouTubeSubscription.Id>(
    createObject = { YouTubeSubscription.Id(it) }
)

class YouTubeVideoIdConverter :
    IdConverter<YouTubeVideo.Id>(createObject = { YouTubeVideo.Id(it) })

class YouTubeChannelLogIdConverter :
    IdConverter<YouTubeChannelLog.Id>(createObject = { YouTubeChannelLog.Id(it) })

class YouTubePlaylistIdConverter :
    IdConverter<YouTubePlaylist.Id>(createObject = { YouTubePlaylist.Id(it) })

class YouTubePlaylistItemIdConverter :
    IdConverter<YouTubePlaylistItem.Id>(createObject = { YouTubePlaylistItem.Id(it) })

class BigIntegerConverter : Converter<Long, BigInteger>(
    serialize = { it.toLong() },
    createObject = { BigInteger.valueOf(it) }
)
