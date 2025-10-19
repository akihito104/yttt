package com.freshdigitable.yttt.data.source.local

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LiveLocalPagingSource @Inject constructor(
    private val dao: LiveDao,
) : LiveDataPagingSource {
    override val onAir: PagingSource<Int, out LiveTimelineItem> get() = dao.getAllOnAirPagingSource()
    override fun upcoming(current: Instant): PagingSource<Int, out LiveTimelineItem> =
        dao.getAllUpcomingPagingSource(current)

    override val freeChat: PagingSource<Int, out LiveTimelineItem> get() = dao.getAllFreeChatPagingSource()
}
