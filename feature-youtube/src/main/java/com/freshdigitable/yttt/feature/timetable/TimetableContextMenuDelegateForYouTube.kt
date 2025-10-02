package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.mapTo
import javax.inject.Inject

internal class TimetableContextMenuDelegateForYouTube @Inject constructor(
    private val repository: YouTubeRepository,
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuSelector {
    override fun findMenuItems(video: LiveVideo<*>): List<TimetableMenuItem> = when (video) {
        is LiveVideo.FreeChat -> listOf(
            TimetableMenuItem.REMOVE_FREE_CHAT,
            if (video.isPinned) TimetableMenuItem.UNPIN else TimetableMenuItem.PIN_TO_TOP,
            TimetableMenuItem.LAUNCH_LIVE,
        )

        else -> listOf(
            TimetableMenuItem.ADD_FREE_CHAT,
            TimetableMenuItem.LAUNCH_LIVE,
        )
    }

    override suspend fun consumeMenuItem(video: LiveVideo<*>, item: TimetableMenuItem) {
        val id = video.id
        when (item) {
            TimetableMenuItem.ADD_FREE_CHAT -> repository.addFreeChatItems(setOf(id.mapTo()))
            TimetableMenuItem.REMOVE_FREE_CHAT -> repository.removeFreeChatItems(setOf(id.mapTo()))
            TimetableMenuItem.LAUNCH_LIVE -> launchApp(video.url)
            TimetableMenuItem.PIN_TO_TOP -> repository.addPinnedVideo(id.mapTo())
            TimetableMenuItem.UNPIN -> repository.removePinnedVideo(id.mapTo())
        }
    }
}
