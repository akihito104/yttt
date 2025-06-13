package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

interface Updatable {
    val maxAge: Duration?
    val fetchedAt: Instant?

    companion object {
        fun Updatable.isUpdatable(current: Instant): Boolean {
            val f = fetchedAt ?: return true
            val a = maxAge ?: return true
            return f + a <= current
        }
    }
}
