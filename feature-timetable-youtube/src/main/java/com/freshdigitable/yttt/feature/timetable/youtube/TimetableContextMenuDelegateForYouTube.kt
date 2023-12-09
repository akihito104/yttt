package com.freshdigitable.yttt.feature.timetable.youtube

import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuSelector
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import javax.inject.Inject

internal class TimetableContextMenuDelegateForYouTube @Inject constructor(
    private val repository: YouTubeRepository,
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuSelector {
    override fun findMenuItems(video: LiveVideo): List<TimetableMenuItem> {
        return listOfNotNull(
            if (video.isFreeChat == true) TimetableMenuItem.REMOVE_FREE_CHAT else TimetableMenuItem.ADD_FREE_CHAT,
            TimetableMenuItem.LAUNCH_LIVE,
        )
    }

    override suspend fun consumeMenuItem(video: LiveVideo, item: TimetableMenuItem) {
        val id = video.id
        when (item) {
            TimetableMenuItem.ADD_FREE_CHAT -> {
                repository.addFreeChatItems(listOf(id.mapTo()))
            }

            TimetableMenuItem.REMOVE_FREE_CHAT -> {
                repository.removeFreeChatItems(listOf(id.mapTo()))
            }

            TimetableMenuItem.LAUNCH_LIVE -> {
                launchApp(video.url)
            }
        }
    }
}
