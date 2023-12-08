package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.model.LiveVideo

interface TimetableContextMenuSelector {
    fun findMenuItems(video: LiveVideo): List<TimetableMenuItem>
    suspend fun consumeMenuItem(video: LiveVideo, item: TimetableMenuItem)
}

enum class TimetableMenuItem(val text: String) {
    ADD_FREE_CHAT("check as free chat"),
    REMOVE_FREE_CHAT("uncheck as free chat"),
    LAUNCH_LIVE("watch live"),
    ;
}
