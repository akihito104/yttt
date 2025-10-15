package com.freshdigitable.yttt.data.source.local

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LiveLocalPagingSource @Inject constructor(
    private val dao: LiveDao,
    private val dateTimeProvider: DateTimeProvider,
) : LiveDataPagingSource {
    override val onAir: PagingSource<Int, out LiveTimelineItem> get() = dao.getAllOnAirPagingSource()
    override val upcoming: PagingSource<Int, out LiveTimelineItem>
        get() = dao.getAllUpcomingPagingSource(dateTimeProvider.now())
    override val freeChat: PagingSource<Int, out LiveTimelineItem> get() = dao.getAllFreeChatPagingSource()
}
