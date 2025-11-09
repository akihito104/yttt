package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Stable
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.insertSeparators
import androidx.paging.map
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

internal interface TimetablePageDelegate {
    fun getTimetableItemPager(page: TimetablePage): (Instant) -> Flow<PagingData<TimetableItem>>
}

internal class TimetablePageDelegateImpl @Inject constructor(
    livePagingSource: LiveDataPagingSource,
    private val settingRepository: SettingRepository,
) : TimetablePageDelegate {
    private val pageFactory: Map<TimetablePage, (Instant) -> PagingSource<Int, out LiveVideo>> = mapOf(
        TimetablePage.OnAir to { livePagingSource.onAir },
        TimetablePage.Upcoming to { livePagingSource.upcoming(it) },
        TimetablePage.FreeChat to { livePagingSource.freeChat },
    )
    private val pagingDataFactory:
        Map<TimetablePage, PagingData<out LiveVideo>.(TimeAdjustment) -> PagingData<TimetableItem>> =
        mapOf(
            TimetablePage.OnAir to { createVideo(it) },
            TimetablePage.Upcoming to { createGroupedVideoWithHeader(it) },
            TimetablePage.FreeChat to { createVideo(it) },
        )

    override fun getTimetableItemPager(page: TimetablePage): (Instant) -> Flow<PagingData<TimetableItem>> {
        val factory = checkNotNull(pageFactory[page])
        val creator = checkNotNull(pagingDataFactory[page])
        return { current ->
            combine(
                Pager(pageConfig) { factory(current) }.flow,
                settingRepository.timeAdjustment,
            ) { pagingData, timeAdjustment -> pagingData.creator(timeAdjustment) }
        }
    }

    companion object {
        private val pageConfig = PagingConfig(5)

        private fun PagingData<out LiveVideo>.createVideo(
            timeAdjustment: TimeAdjustment,
        ): PagingData<TimetableItem> = map { TimetableItem.Video(it, timeAdjustment) }

        private fun PagingData<out LiveVideo>.createGroupedVideoWithHeader(
            timeAdjustment: TimeAdjustment,
        ): PagingData<TimetableItem> = map { TimetableItem.GroupedVideo(it, timeAdjustment) }
            .insertSeparators { before, after ->
                if (before == null && after != null) {
                    TimetableItem.Header(after.key.text)
                } else if (isHeaderNeeded(before, after)) {
                    TimetableItem.Header(checkNotNull(after).key.text)
                } else {
                    null
                }
            }

        private fun isHeaderNeeded(before: TimetableItem.GroupedVideo?, after: TimetableItem.GroupedVideo?): Boolean {
            if (before == null || after == null) {
                return false
            }
            return before.key != after.key
        }
    }
}

sealed interface TimetableItem {
    val adjustedDateTime: String

    @Stable
    open class Video(
        video: LiveVideo,
        timeAdjustment: TimeAdjustment,
    ) : TimetableItem, LiveVideo by video {
        override val adjustedDateTime: String = dateTime.toAdjustedLocalDateTimeText(timeAdjustment)
    }

    @Stable
    class GroupedVideo(
        video: LiveVideo,
        timeAdjustment: TimeAdjustment,
    ) : Video(video, timeAdjustment) {
        internal val key: GroupKey = GroupKey.create(dateTime, timeAdjustment.extraHourOfDay, timeAdjustment.zoneId)
    }

    @Stable
    class Header(
        override val adjustedDateTime: String,
    ) : TimetableItem
}
