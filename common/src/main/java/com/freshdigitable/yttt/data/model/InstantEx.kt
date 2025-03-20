package com.freshdigitable.yttt.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

interface DateTimeProvider {
    fun now(): Instant
}

/**
 * yyyy/MM/dd
 */
val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/**
 * yyyy/MM/dd(E)
 */
val dateWeekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd(E)")

/**
 * yyyy/MM/dd(E) HH:mm
 */
fun dateTimeFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd(E) HH:mm", locale)

/**
 * yyyy/MM/dd(E) HH:mm:ss
 */
fun dateTimeSecondFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd(E) HH:mm:ss", locale)

fun Instant.toLocalFormattedText(
    formatter: DateTimeFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = LocalDateTime.ofInstant(this, zoneId).format(formatter)

fun Instant.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(this, zoneId)
