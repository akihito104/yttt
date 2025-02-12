package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.model.LiveVideo
import javax.inject.Inject

internal class TimetableContextMenuDelegateForTwitch @Inject constructor(
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuSelector {
    override fun findMenuItems(video: LiveVideo<*>): List<TimetableMenuItem> {
        return listOf(TimetableMenuItem.LAUNCH_LIVE)
    }

    override suspend fun consumeMenuItem(video: LiveVideo<*>, item: TimetableMenuItem) {
        if (item == TimetableMenuItem.LAUNCH_LIVE) {
            launchApp(video.url)
        }
    }
}
