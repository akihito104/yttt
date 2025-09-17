package com.freshdigitable.yttt.data.model

import java.time.Instant

interface DateTimeProvider {
    fun now(): Instant
}
