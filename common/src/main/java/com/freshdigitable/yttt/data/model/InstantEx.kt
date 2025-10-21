package com.freshdigitable.yttt.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias DateFormatterPattern = String

const val DATE: DateFormatterPattern = "yyyy/MM/dd"
const val DATE_WEEKDAY: DateFormatterPattern = "yyyy/MM/dd(E)"
const val DATE_WEEKDAY_MINUTES: DateFormatterPattern = "yyyy/MM/dd(E) HH:mm"
const val DATE_WEEKDAY_SECONDS: DateFormatterPattern = "yyyy/MM/dd(E) HH:mm:ss"

fun DateFormatterPattern.toPattern(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern(this, locale)

fun Instant.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(this, zoneId)

fun Instant.toLocalFormattedText(
    formatter: DateTimeFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = toLocalDateTime(zoneId).format(formatter)
