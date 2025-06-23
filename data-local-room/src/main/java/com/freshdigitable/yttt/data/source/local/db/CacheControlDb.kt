package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import com.freshdigitable.yttt.data.model.CacheControl
import java.time.Duration
import java.time.Instant

internal class CacheControlDb(
    @ColumnInfo(name = "fetched_at", defaultValue = "null")
    override val fetchedAt: Instant?,
    @ColumnInfo(name = "max_age", defaultValue = "null")
    override val maxAge: Duration?,
) : CacheControl

internal fun CacheControl.toDb(): CacheControlDb = CacheControlDb(
    fetchedAt = fetchedAt,
    maxAge = maxAge,
)
