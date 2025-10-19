package com.freshdigitable.yttt.feature.timetable

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

internal interface TimetablePageDelegate {
    fun getLiveTimelineItemPager(page: TimetablePage): (Instant) -> Flow<PagingData<LiveTimelineItem>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    livePagingSource: LiveDataPagingSource,
) : TimetablePageDelegate {
    private val pageFactory: Map<TimetablePage, (Instant) -> PagingSource<Int, out LiveTimelineItem>> = mapOf(
        TimetablePage.OnAir to { livePagingSource.onAir },
        TimetablePage.Upcoming to { livePagingSource.upcoming(it) },
        TimetablePage.FreeChat to { livePagingSource.freeChat },
    )

    @OptIn(FlowPreview::class)
    override fun getLiveTimelineItemPager(
        page: TimetablePage,
    ): (Instant) -> Flow<PagingData<LiveTimelineItem>> {
        val factory = checkNotNull(pageFactory[page])
        return { current ->
            Pager(pageConfig) { factory(current) }.flow
                .debounce(100.milliseconds).map { i -> i.map { it } }
        }
    }

    companion object {
        private val pageConfig = PagingConfig(5)
    }
}
