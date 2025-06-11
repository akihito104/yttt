package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

interface Updatable {
    val maxAge: Duration
    val fetchedAt: Instant

    companion object {
        private val Updatable.updatableAt: Instant get() = fetchedAt + maxAge
        fun Updatable.isUpdatable(current: Instant): Boolean = updatableAt <= current
    }
}
