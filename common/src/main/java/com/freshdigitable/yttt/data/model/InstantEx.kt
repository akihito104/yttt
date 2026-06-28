package com.freshdigitable.yttt.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
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

fun Instant.toLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    toLocalDateTime(timeZone)

fun Instant.toLocalFormattedText(
    formatter: DateTimeFormatter,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = toJavaInstant().atZone(timeZone.toJavaZoneId()).toLocalDateTime().format(formatter)

fun LocalDateTime.toLocalFormattedText(
    formatter: DateTimeFormatter,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = toJavaLocalDateTime().atZone(timeZone.toJavaZoneId()).toLocalDateTime().format(formatter)

fun LocalDate.toLocalFormattedText(
    formatter: DateTimeFormatter,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, 0, 0)
    .toLocalFormattedText(formatter, timeZone)

private fun TimeZone.toJavaZoneId(): ZoneId = ZoneId.of(id)
