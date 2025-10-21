package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveVideo

interface TimetableContextMenuSelector {
    suspend fun setupMenuItems(videoId: LiveVideo.Id): ContextMenuSelector<TimetableMenuItem>

    companion object {
        val EMPTY: ContextMenuSelector<TimetableMenuItem> = ContextMenuSelector.empty()
    }
}

interface ContextMenuSelector<T> {
    val menuItems: List<T>
    suspend fun consumeMenuItem(item: T)

    companion object {
        fun <T> empty(): ContextMenuSelector<T> = Empty()
    }

    private class Empty<T> : ContextMenuSelector<T> {
        override val menuItems: List<T> = emptyList()
        override suspend fun consumeMenuItem(item: T) = Unit
    }
}

enum class TimetableMenuItem(val text: String) {
    ADD_FREE_CHAT("check as free chat"),
    REMOVE_FREE_CHAT("uncheck as free chat"),
    LAUNCH_LIVE("watch live"),
    PIN_TO_TOP("pin to top"),
    UNPIN("unpin"),
}
