package com.freshdigitable.yttt.data.source

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.LiveTimelineItem

interface LiveDataPagingSource {
    val onAir: PagingSource<Int, out LiveTimelineItem>
    val upcoming: PagingSource<Int, out LiveTimelineItem>
    val freeChat: PagingSource<Int, out LiveTimelineItem>
}
