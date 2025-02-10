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
    private val factory = items.mapValues { (k, v) -> Factory(k, v, settingRepository) }
    override fun getSimpleItemList(page: TimetablePage): Flow<List<TimelineItem>> =
        checkNotNull(factory[page]).simpleItem

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<TimelineItem>>> =
        checkNotNull(factory[page]).groupedItem

    override val tabs: Flow<List<TimetableTabData>> = combine(factory.map { (_, v) -> v.tab }) {
        it.toList().sorted()
    }

    private class Factory(
        page: TimetablePage,
        useCase: Set<FetchTimetableItemSourceUseCase>,
        settingRepository: SettingRepository,
    ) {
        private val extraHourOfDay = settingRepository.changeDateTime.map {
            Duration.ofHours(((it ?: 24) - 24).toLong())
        }

        @OptIn(FlowPreview::class)
        private val videos = combine(useCase.map { it() }) { v -> v.flatMap { it } }
            .debounce(50.milliseconds)
            .map { v -> v.sortedWith(compareBy { it }) }
            .onEach { logI { "${page.name}: size=${it.size}" } }
        val simpleItem = combine(videos, extraHourOfDay) { v, e ->
            v.map { TimelineItem(it, e) }
        }
        val groupedItem = simpleItem.map { v ->
            v.filter { it.groupKey != null }
                .groupBy { checkNotNull(it.groupKey) }
                .mapKeys { (k, _) -> k.text }
        }
        val tab = videos.map { it.size }
            .distinctUntilChanged()
            .map { TimetableTabData(page, it) }
    }
}
