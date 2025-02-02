package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideo.Upcoming.Companion.scheduledStartLocalDateWithOffset
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.feature.timetable.UpcomingLiveVideoImpl.Companion.asUpcoming
import com.freshdigitable.yttt.logI
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

internal interface TimetablePageDelegate {
    fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>>
    fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>>
    val tabs: Flow<List<TimetableTabData>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    items: Map<TimetablePage, @JvmSuppressWildcards Set<FetchTimetableItemSourceUseCase>>,
    settingRepository: SettingRepository,
) : TimetablePageDelegate {
    private val sorterTable = mapOf<TimetablePage, (List<LiveVideo>) -> List<LiveVideo>>(
        TimetablePage.OnAir to { i ->
            i.sortedByDescending { it.actualStartDateTime }
        },
        TimetablePage.Upcoming to { i ->
            i.sortedBy { it.scheduledStartDateTime }
        },
        TimetablePage.FreeChat to { i ->
            i.sortedBy { it.channel.id.value }
        },
    )

    @OptIn(FlowPreview::class)
    private val sourceTable: Map<TimetablePage, Flow<List<LiveVideo>>> = items.entries
        .associate { (p, uc) ->
            val sorter = checkNotNull(sorterTable[p])
            p to combine(uc.map { it().distinctUntilChanged() }) { v -> v.flatMap { it } }
                .debounce(50.milliseconds)
                .map { sorter(it) }
                .onEach { logI { "${p.name}: size=${it.size}" } }
        }

    override fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>> =
        checkNotNull(sourceTable[page])

    private val extraHourOfDay = settingRepository.changeDateTime.map {
        Duration.ofHours(((it ?: 24) - 24).toLong())
    }
    private val upcomingItems: Flow<Map<String, List<LiveVideo>>> =
        combine(sourceTable[TimetablePage.Upcoming]!!, extraHourOfDay) { v, t ->
            v.map { it.asUpcoming(t) }
                .groupBy { it.scheduledStartLocalDateWithOffset().format(dateWeekdayFormatter) }
        }
    private val groupedItemLists = mapOf(
        TimetablePage.Upcoming to upcomingItems,
    )

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>> =
        checkNotNull(groupedItemLists[page])

    override val tabs: Flow<List<TimetableTabData>> = combine(
        sourceTable.entries.map { (k, v) ->
            v.map { it.size }.distinctUntilChanged().map { TimetableTabData(k, it) }
        }
    ) {
        it.toList().sorted()
    }
}

internal data class UpcomingLiveVideoImpl internal constructor(
    private val liveVideo: LiveVideo,
    override val offset: Duration,
) : LiveVideo.Upcoming, LiveVideo by liveVideo {
    override val scheduledStartDateTime: Instant
        get() = checkNotNull(liveVideo.scheduledStartDateTime)

    companion object {
        fun LiveVideo.asUpcoming(offset: Duration): LiveVideo.Upcoming = when (this) {
            is LiveVideo.Upcoming -> this
            else -> UpcomingLiveVideoImpl(this, offset)
        }
    }
}
