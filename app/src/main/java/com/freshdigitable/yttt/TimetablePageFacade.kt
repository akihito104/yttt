package com.freshdigitable.yttt

import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.model.toTwitchVideoList
import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

interface TimetablePageFacade {
    fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>>
    fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>>
    val tabs: Flow<List<TabData>>
}

interface FetchTimetableItemSourceUseCase {
    operator fun invoke(): Flow<List<LiveVideo>>
}

class FetchTwitchOnAirItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override operator fun invoke(): Flow<List<LiveVideo>> = repository.onAir.map {
        it.map { s ->
            val user = s.user as? TwitchUserDetail
                ?: repository.findUsersById(listOf(s.user.id)).first()
            s.toLiveVideo(user)
        }
    }
}

class FetchYouTubeOnAirItemSourceUseCase @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isNowOnAir() } }
}

class FetchYouTubeUpcomingItemSourceUseCase @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isUpcoming() && it.isFreeChat != true } }
}

class FetchTwitchUpcomingItemSourceUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> = repository.upcoming.map { u ->
        val week = Instant.now().plus(Duration.ofDays(7L))
        u.map { s -> s.toTwitchVideoList() }.flatten()
            .filter { it.schedule.startTime.isBefore(week) }
            .map { s ->
                val user = s.user as? TwitchUserDetail
                    ?: repository.findUsersById(listOf(s.user.id)).first()
                s.toLiveVideo(user)
            }
    }
}

class FetchYouTubeFreeChatItemSourceUseCase @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isFreeChat == true } }
}

class TimetablePageFacadeImpl @Inject constructor(
    items: Map<TimetablePage, @JvmSuppressWildcards Set<FetchTimetableItemSourceUseCase>>,
    prefs: AndroidPreferencesDataStore,
) : TimetablePageFacade {
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
    private val sourceTable: Map<TimetablePage, Flow<List<LiveVideo>>> = items.entries
        .associate { (p, uc) ->
            val sorter = checkNotNull(sorterTable[p])
            p to combine(uc.map { it().distinctUntilChanged() }) { v -> v.flatMap { it } }
                .map { sorter(it) }
        }

    override fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>> =
        checkNotNull(sourceTable[page])

    private val extraHourOfDay = prefs.changeDateTime.map {
        Duration.ofHours(((it ?: 24) - 24).toLong())
    }
    private val upcomingItems: Flow<Map<String, List<LiveVideo>>> =
        combine(sourceTable[TimetablePage.Upcoming]!!, extraHourOfDay) { v, t ->
            v.groupBy {
                (checkNotNull(it.scheduledStartDateTime) - t)
                    .toLocalDateTime()
                    .truncatedTo(ChronoUnit.DAYS)
                    .format(dateWeekdayFormatter)
            }
        }
    private val groupedItemLists = mapOf(
        TimetablePage.Upcoming to upcomingItems,
    )

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>> =
        checkNotNull(groupedItemLists[page])

    override val tabs: Flow<List<TabData>> = combine(
        sourceTable.entries.map { (k, v) ->
            v.map { it.size }.distinctUntilChanged().map { TabData(k, it) }
        }
    ) {
        it.toList().sorted()
    }
}
