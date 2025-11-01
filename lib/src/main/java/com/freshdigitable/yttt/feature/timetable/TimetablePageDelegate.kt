package com.freshdigitable.yttt.feature.timetable

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

internal interface TimetablePageDelegate {
    fun getLiveVideoPager(page: TimetablePage): (Instant) -> Flow<PagingData<LiveVideo>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    livePagingSource: LiveDataPagingSource,
) : TimetablePageDelegate {
    private val pageFactory: Map<TimetablePage, (Instant) -> PagingSource<Int, out LiveVideo>> = mapOf(
        TimetablePage.OnAir to { livePagingSource.onAir },
        TimetablePage.Upcoming to { livePagingSource.upcoming(it) },
        TimetablePage.FreeChat to { livePagingSource.freeChat },
    )

    override fun getLiveVideoPager(
        page: TimetablePage,
    ): (Instant) -> Flow<PagingData<LiveVideo>> {
        val factory = checkNotNull(pageFactory[page])
        return { current ->
            Pager(pageConfig) { factory(current) }.flow.map { i -> i.map { it } }
        }
    }

    companion object {
        private val pageConfig = PagingConfig(5)
    }
}
