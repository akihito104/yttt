package com.freshdigitable.yttt.data.source.local.db

import androidx.room.TypeConverter
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
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

class LiveChannelIdConverter :
    IdConverter<LiveChannel.Id>(createObject = { LiveChannel.Id(it, LivePlatform.YOUTUBE) })

class LiveSubscriptionIdConverter : IdConverter<LiveSubscription.Id>(
    createObject = { LiveSubscription.Id(it, LivePlatform.YOUTUBE) }
)

class LiveVideoIdConverter :
    IdConverter<LiveVideo.Id>(createObject = { LiveVideo.Id(it, LivePlatform.YOUTUBE) })

class LiveChannelLogIdConverter :
    IdConverter<LiveChannelLog.Id>(createObject = { LiveChannelLog.Id(it) })

class LivePlaylistIdConverter : IdConverter<LivePlaylist.Id>(createObject = { LivePlaylist.Id(it) })
class LivePlaylistItemIdConverter :
    IdConverter<LivePlaylistItem.Id>(createObject = { LivePlaylistItem.Id(it) })

class BigIntegerConverter : Converter<Long, BigInteger>(
    serialize = { it.toLong() },
    createObject = { BigInteger.valueOf(it) }
)
