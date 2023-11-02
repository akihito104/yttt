package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser

class TwitchUserIdConverter : IdConverter<TwitchUser.Id>(createObject = { TwitchUser.Id(it) })
class TwitchStreamScheduleIdConverter :
    IdConverter<TwitchChannelSchedule.Stream.Id>(createObject = { TwitchChannelSchedule.Stream.Id(it) })

class TwitchStreamIdConverter : IdConverter<TwitchStream.Id>(createObject = { TwitchStream.Id(it) })
class CsvConverter : Converter<String, List<@JvmSuppressWildcards String>>(
    serialize = { it.joinToString(separator = ",") },
    createObject = { it.split(",") },
)
