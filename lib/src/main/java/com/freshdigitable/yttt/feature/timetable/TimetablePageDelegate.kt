package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataSource
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

internal interface TimetablePageDelegate {
    fun getSimpleItemList(page: TimetablePage): Flow<List<TimelineItem>>
    fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<TimelineItem>>>
    val tabs: Flow<List<TimetableTabData>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    liveDataSource: LiveDataSource,
    settingRepository: SettingRepository,
) : TimetablePageDelegate {
    private val factory = mapOf(
        TimetablePage.OnAir to Factory(TimetablePage.OnAir, { liveDataSource.onAir }, settingRepository),
        TimetablePage.Upcoming to Factory(TimetablePage.Upcoming, { liveDataSource.upcoming }, settingRepository),
        TimetablePage.FreeChat to Factory(TimetablePage.FreeChat, { liveDataSource.freeChat }, settingRepository),
    )

    override fun getSimpleItemList(page: TimetablePage): Flow<List<TimelineItem>> =
        checkNotNull(factory[page]).simpleItem

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<TimelineItem>>> =
        checkNotNull(factory[page]).groupedItem

    override val tabs: Flow<List<TimetableTabData>> = combine(factory.map { (_, v) -> v.tab }) {
        it.toList().sorted()
    }

    private class Factory(
        page: TimetablePage,
        useCase: () -> Flow<List<LiveTimelineItem>>,
        settingRepository: SettingRepository,
    ) {
        private val timeAdjustment = settingRepository.changeDateTime.map {
            TimeAdjustment(Duration.ofHours(((it ?: 24) - 24).toLong()))
        }

        @OptIn(FlowPreview::class)
        private val videos = combine(useCase()) { v -> v.flatMap { it } }
            .debounce(50.milliseconds)
            .onEach { logI { "${page.name}: size=${it.size}" } }
        val simpleItem = combine(videos, timeAdjustment) { v, a ->
            when (page.type) {
                TimetablePage.Type.SIMPLE -> v.map { TimelineItem.Simple(it, a) }
                TimetablePage.Type.GROUPED -> v.map { TimelineItem.Grouped(it, a) }
            }
        }
        val groupedItem = simpleItem.map { v ->
            v.filterIsInstance<TimelineItem.Grouped>()
                .groupBy { checkNotNull(it.groupKey) }
                .mapKeys { (k, _) -> k.text }
        }
        val tab = videos.map { it.size }
            .distinctUntilChanged()
            .map { TimetableTabData(page, it) }
    }
}
