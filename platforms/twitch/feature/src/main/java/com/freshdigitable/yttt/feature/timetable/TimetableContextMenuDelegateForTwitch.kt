package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import javax.inject.Inject

internal class TimetableContextMenuDelegateForTwitch @Inject constructor(
    private val dataSource: TwitchStreamDataSource.Extended,
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuSelector {
    override suspend fun setupMenuItems(videoId: LiveVideo.Id): ContextMenuSelector<TimetableMenuItem> {
        val twitchId = when (videoId.type) {
            TwitchStream.Id::class -> videoId.mapTo<TwitchStream.Id>()
            TwitchChannelSchedule.Stream.Id::class -> videoId.mapTo<TwitchChannelSchedule.Stream.Id>()
            else -> throw AssertionError("unsupported type: ${videoId.type}")
        }
        val selected = dataSource.fetchStreamDetail(twitchId)
        return object : ContextMenuSelector<TimetableMenuItem> {
            override val menuItems: List<TimetableMenuItem>
                get() = listOf(TimetableMenuItem.LAUNCH_LIVE)

            override suspend fun consumeMenuItem(item: TimetableMenuItem) {
                if (item == TimetableMenuItem.LAUNCH_LIVE) {
                    launchApp(checkNotNull(selected).url)
                }
            }
        }
    }
}
