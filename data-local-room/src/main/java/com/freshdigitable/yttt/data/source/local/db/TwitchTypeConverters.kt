package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser

internal class TwitchUserIdConverter : IdConverter<TwitchUser.Id>(createObject = { TwitchUser.Id(it) })
internal class TwitchStreamScheduleIdConverter :
    IdConverter<TwitchChannelSchedule.Stream.Id>(createObject = { TwitchChannelSchedule.Stream.Id(it) })

internal class TwitchStreamIdConverter : IdConverter<TwitchStream.Id>(createObject = { TwitchStream.Id(it) })
internal class CsvConverter : Converter<String, List<@JvmSuppressWildcards String>>(
    serialize = { it.joinToString(separator = ",") },
    createObject = { it.split(",") },
)
