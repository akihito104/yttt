package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import javax.inject.Inject

internal class TimetableContextMenuDelegateForYouTube @Inject constructor(
    private val extendedSource: YouTubeDataSource.Extended,
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuSelector {
    override suspend fun setupMenuItems(videoId: LiveVideo.Id): ContextMenuSelector<TimetableMenuItem> {
        val youtubeId = videoId.mapTo<YouTubeVideo.Id>()
        val video = extendedSource.fetchVideoList(setOf(youtubeId)).getOrThrow().first().item
        return object : ContextMenuSelector<TimetableMenuItem> {
            override val menuItems: List<TimetableMenuItem>
                get() = when {
                    video.isFreeChat == true -> listOf(
                        TimetableMenuItem.REMOVE_FREE_CHAT,
                        if (video.isPinned == true) TimetableMenuItem.UNPIN else TimetableMenuItem.PIN_TO_TOP,
                        TimetableMenuItem.LAUNCH_LIVE,
                    )

                    else -> listOf(
                        TimetableMenuItem.ADD_FREE_CHAT,
                        TimetableMenuItem.LAUNCH_LIVE,
                    )
                }

            override suspend fun consumeMenuItem(item: TimetableMenuItem) {
                when (item) {
                    TimetableMenuItem.ADD_FREE_CHAT -> extendedSource.addFreeChatItems(setOf(video.id))
                    TimetableMenuItem.REMOVE_FREE_CHAT -> extendedSource.removeFreeChatItems(setOf(video.id))
                    TimetableMenuItem.LAUNCH_LIVE -> launchApp(video.url)
                    TimetableMenuItem.PIN_TO_TOP -> extendedSource.addPinnedVideo(video.id)
                    TimetableMenuItem.UNPIN -> extendedSource.removePinnedVideo(video.id)
                }
            }
        }
    }
}
