package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.feature.channel.YouTubeChannelSectionFacade.FetchTaskItems.Companion.ITEM_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal class YouTubeChannelSectionFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> =
        repository.fetchChannelSection(id)

    fun watchTasks(task: FetchTaskItems): Flow<FetchTaskResult> = combine(
        watchPlaylist(task).onStart { emit(Result.success(emptyMap())) },
        watchPlaylistItem(task).onStart { emit(emptyMap()) },
        watchChannel(task).onStart { emit(Result.success(emptyMap())) },
    ) { p, pi, c ->
        FetchTaskResult(p, pi, c)
    }

    private fun watchPlaylist(task: FetchTaskItems): Flow<Result<Map<YouTubePlaylist.Id, YouTubePlaylist>>> {
        val item = task.playlist
        if (item.isEmpty()) {
            return emptyFlow()
        }
        return flowOf(item).map { p ->
            repository.fetchPlaylist(p).map { r -> r.associateBy { it.id } }
        }
    }

    private fun watchPlaylistItem(task: FetchTaskItems): Flow<Map<YouTubePlaylist.Id, Result<List<YouTubePlaylistItem>>>> {
        val item = task.playlistItem
        if (item.isEmpty()) {
            return emptyFlow()
        }
        return flow {
            val res = item.map { i ->
                flowOf(i).map { it to repository.fetchPlaylistItems(it, ITEM_SIZE.toLong()) }
            }
            emitAll(combine(res) { it.toMap() })
        }
    }

    private fun watchChannel(task: FetchTaskItems): Flow<Result<Map<YouTubeChannel.Id, YouTubeChannel>>> {
        val item = task.channel
        if (item.isEmpty()) {
            return emptyFlow()
        }
        return flowOf(item).map { c ->
            repository.fetchChannelList(c).map { res -> res.associateBy { it.id } }
        }
    }

    class FetchTaskItems {
        companion object {
            const val ITEM_SIZE = 10
            fun create(sections: Collection<YouTubeChannelSection>): FetchTaskItems =
                sections.fold(FetchTaskItems()) { acc, s -> acc.fold(s) }
        }

        private val _playlist = mutableSetOf<YouTubePlaylist.Id>()
        val playlist: Set<YouTubePlaylist.Id> get() = _playlist
        private val _playlistItem = mutableSetOf<YouTubePlaylist.Id>()
        val playlistItem: Set<YouTubePlaylist.Id> get() = _playlistItem
        private val _channel = mutableSetOf<YouTubeChannel.Id>()
        val channel: Set<YouTubeChannel.Id> get() = _channel

        private fun fold(section: YouTubeChannelSection): FetchTaskItems {
            when (val content = section.content) {
                is YouTubeChannelSection.Content.Playlist -> {
                    if (section.type == YouTubeChannelSection.Type.SINGLE_PLAYLIST) {
                        _playlist.addAll(content.item)
                        _playlistItem.addAll(content.item)
                    } else {
                        _playlist.addAll(content.item.take(ITEM_SIZE))
                    }
                }

                is YouTubeChannelSection.Content.Channels -> {
                    _channel.addAll(content.item.take(ITEM_SIZE))
                }

                null -> {}
            }
            return this
        }
    }

    class FetchTaskResult(
        playlistResult: Result<Map<YouTubePlaylist.Id, YouTubePlaylist>>,
        playlistItemResult: Map<YouTubePlaylist.Id, Result<List<YouTubePlaylistItem>>>,
        channelResult: Result<Map<YouTubeChannel.Id, YouTubeChannel>>,
    ) {
        val playlist = playlistResult.getOrDefault(emptyMap())
        val playlistItem = playlistItemResult.mapValues { it.value.getOrDefault(emptyList()) }
        val channel = channelResult.getOrDefault(emptyMap())
        val failure = listOfNotNull(
            playlistResult.exceptionOrNull(),
            channelResult.exceptionOrNull(),
        ) + playlistItemResult.values.mapNotNull { it.exceptionOrNull() }
    }
}

internal sealed interface ChannelSectionItem : Comparable<ChannelSectionItem> {
    val section: YouTubeChannelSection
    val title: String
        get() = section.title ?: section.type.name
    val size: Int
        get() = 0

    override fun compareTo(other: ChannelSectionItem): Int = section.compareTo(other.section)

    companion object {
        fun create(
            section: YouTubeChannelSection,
            result: YouTubeChannelSectionFacade.FetchTaskResult,
        ): ChannelSectionItem = when (val content = section.content) {
            is YouTubeChannelSection.Content.Playlist -> {
                when (section.type) {
                    YouTubeChannelSection.Type.SINGLE_PLAYLIST -> SinglePlaylist(
                        section = section,
                        playlist = result.playlist[content.item.first()],
                        item = result.playlistItem[content.item.first()] ?: emptyList(),
                    )

                    YouTubeChannelSection.Type.MULTIPLE_PLAYLIST -> MultiPlaylist(
                        section = section,
                        item = content.item.mapNotNull { result.playlist[it] },
                    )

                    else -> EmptySection(section)
                }
            }

            is YouTubeChannelSection.Content.Channels -> MultipleChannel(
                section = section,
                item = content.item.mapNotNull { result.channel[it] },
            )

            null -> EmptySection(section)
        }
    }
}

internal data class SinglePlaylist(
    override val section: YouTubeChannelSection,
    val playlist: YouTubePlaylist?,
    val item: List<YouTubePlaylistItem>,
) : ChannelSectionItem {
    override val title: String get() = playlist?.title ?: super.title
    override val size: Int get() = item.size
}

internal data class MultiPlaylist(
    override val section: YouTubeChannelSection,
    val item: List<YouTubePlaylist>,
) : ChannelSectionItem {
    override val size: Int get() = item.size
}

internal data class MultipleChannel(
    override val section: YouTubeChannelSection,
    val item: List<YouTubeChannel>,
) : ChannelSectionItem {
    override val size: Int get() = item.size
}

internal data class EmptySection(override val section: YouTubeChannelSection) : ChannelSectionItem
