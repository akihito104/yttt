package com.freshdigitable.yttt.data.model

import kotlin.time.Instant

interface DateTimeProvider {
    fun now(): Instant
}
