package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataSource
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

internal interface TimetablePageDelegate {
    fun getLiveTimelineItemList(page: TimetablePage): Flow<List<LiveTimelineItem>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    liveDataSource: LiveDataSource,
) : TimetablePageDelegate {
    private val factory = mapOf(
        TimetablePage.OnAir to { liveDataSource.onAir },
        TimetablePage.Upcoming to { liveDataSource.upcoming },
        TimetablePage.FreeChat to { liveDataSource.freeChat },
    )

    @OptIn(FlowPreview::class)
    override fun getLiveTimelineItemList(page: TimetablePage): Flow<List<LiveTimelineItem>> =
        checkNotNull(factory[page])().debounce(50.milliseconds)
            .onEach { logI { "${page.name}: size=${it.size}" } }
}
