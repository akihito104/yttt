package com.freshdigitable.yttt.data.model

import kotlinx.datetime.Instant

interface DateTimeProvider {
    fun now(): Instant
}
