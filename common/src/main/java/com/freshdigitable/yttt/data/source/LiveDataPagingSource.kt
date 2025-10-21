package com.freshdigitable.yttt.data.source

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.LiveVideo
import java.time.Instant

interface LiveDataPagingSource {
    val onAir: PagingSource<Int, out LiveVideo>
    fun upcoming(current: Instant): PagingSource<Int, out LiveVideo>
    val freeChat: PagingSource<Int, out LiveVideo>
}
