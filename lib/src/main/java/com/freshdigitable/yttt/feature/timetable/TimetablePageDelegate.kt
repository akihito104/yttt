package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.data.SettingRepository
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
    items: Map<TimetablePage, @JvmSuppressWildcards Set<FetchTimetableItemSourceUseCase>>,
    settingRepository: SettingRepository,
) : TimetablePageDelegate {
    private val extraHourOfDay = settingRepository.changeDateTime.map {
        Duration.ofHours(((it ?: 24) - 24).toLong())
    }

    @OptIn(FlowPreview::class)
    private val sourceTable: Map<TimetablePage, Flow<List<TimelineItem>>> =
        items.mapValues { (p, uc) ->
            val videos = combine(uc.map { it() }) { v -> v.flatMap { it } }
                .debounce(50.milliseconds)
                .map { v -> v.sortedWith(compareBy { it }) }
                .onEach { logI { "${p.name}: size=${it.size}" } }
            combine(videos, extraHourOfDay) { v, e -> v.map { TimelineItem(it, e) } }
        }

    override fun getSimpleItemList(page: TimetablePage): Flow<List<TimelineItem>> =
        checkNotNull(sourceTable[page])

    private val upcomingItems: Flow<Map<String, List<TimelineItem>>> =
        checkNotNull(sourceTable[TimetablePage.Upcoming]).map { v ->
            v.groupBy { checkNotNull(it.groupKey) }.mapKeys { (k, _) -> k.text }
        }
    private val groupedItemLists = mapOf(
        TimetablePage.Upcoming to upcomingItems,
    )

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<TimelineItem>>> =
        checkNotNull(groupedItemLists[page])

    override val tabs: Flow<List<TimetableTabData>> = combine(
        sourceTable.entries.map { (k, v) ->
            v.map { it.size }.distinctUntilChanged().map { TimetableTabData(k, it) }
        }
    ) {
        it.toList().sorted()
    }
}
