package com.freshdigitable.yttt.data.source

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import java.time.Instant

interface LiveDataPagingSource {
    val onAir: PagingSource<Int, out LiveTimelineItem>
    fun upcoming(current: Instant): PagingSource<Int, out LiveTimelineItem>
    val freeChat: PagingSource<Int, out LiveTimelineItem>
}
