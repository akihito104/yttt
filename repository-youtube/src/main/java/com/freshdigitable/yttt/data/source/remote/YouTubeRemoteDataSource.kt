package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.flattenToList
import com.freshdigitable.yttt.data.model.Updatable.Companion.map
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemIds
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import java.time.Instant

internal class YouTubeRemoteDataSource(
    private val youtube: YouTubeClient,
    private val ioScope: IoScope,
) : YouTubeDataSource.Remote {
    override fun fetchSubscriptions(pageSize: Long): Flow<Result<YouTubeSubscriptions.Paged>> =
        ioScope.asResultFlow {
            var paged = PagedSubscription()
            do {
                val res = youtube.fetchSubscription(pageSize, paged.itemSize, paged.nextPageToken)
                paged = paged.update(res.item, res.nextPageToken, res.cacheControl.fetchedAt)
                emit(Result.success(paged))
            } while (paged.nextPageToken != null)
        }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): Result<List<YouTubeChannelLog>> = fetchAllItems {
        fetchLiveChannelLogs(channelId, publishedAfter, maxResult, it)
    }

    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideo>>> =
        fetchList(ids) { fetchVideoList(it) }

    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<Updatable<YouTubeChannelDetail>>> =
        fetchList(ids) { fetchChannelList(it) }

    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> =
        fetch { fetchChannelSection(id) }.map { it.item }

    override suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): Result<Updatable<List<YouTubePlaylistItem>>> = fetch { fetchPlaylistItems(id, maxResult) }

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> =
        fetchList(ids) { fetchPlaylist(it) }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItems<out YouTubePlaylistItemIds>?
    ): Result<Updatable<YouTubePlaylistWithItemDetails>> = fetch {
        if (cache != null) {
            val res = fetchPlaylistItems(id, maxResult)
            cache.update(res)
        } else {
            val playlist = fetchPlaylist(setOf(id)).map { it.first() }
            val items = fetchPlaylistItems(id, maxResult)
            @Suppress("UNCHECKED_CAST")
            YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist,
                items = items as Updatable<List<YouTubePlaylistItem>?>,
            )
        }
    }.recoverCatching {
        if ((it as? NetworkResponse.Exception)?.statusCode == 404) {
            val cacheControl = it.cacheControl
            cache?.update(emptyList<YouTubePlaylistItem>().toUpdatable(cacheControl))
                ?: YouTubePlaylistWithItems.newPlaylist(
                    playlist = YouTubePlaylistNotFound(id).toUpdatable(cacheControl),
                    items = Updatable.create(null, cacheControl),
                )
        } else {
            throw it
        }
    }

    private suspend inline fun <E> fetchAllItems(
        crossinline request: YouTubeClient.(String?) -> NetworkResponse<List<E>>,
    ): Result<List<E>> = ioScope.asResult {
        buildList {
            var token: String? = null
            do {
                val response = youtube.request(token)
                addAll(response.item)
                token = response.nextPageToken
            } while (token != null)
        }
    }

    private suspend inline fun <I : IdBase, E> fetchList(
        ids: Set<I>,
        crossinline request: YouTubeClient.(Set<I>) -> NetworkResponse<List<E>>,
    ): Result<List<Updatable<E>>> = ioScope.asResult {
        if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size <= YouTubeDataSource.MAX_BATCH_SIZE) {
            youtube.request(ids).flattenToList()
        } else {
            ids.chunked(YouTubeDataSource.MAX_BATCH_SIZE)
                .map { async { youtube.request(it.toSet()).flattenToList() } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend inline fun <T> fetch(crossinline request: YouTubeClient.() -> Updatable<T>): Result<Updatable<T>> =
        ioScope.asResult { youtube.request() }
}

private data class PagedSubscription(
    private val pages: List<List<YouTubeSubscription>> = emptyList(),
    val nextPageToken: String? = null,
    private val updatedAt: Instant? = null,
) : YouTubeSubscriptions.Paged {
    override val items: List<YouTubeSubscription> get() = pages.flatten()
    override val lastUpdatedAt: Instant get() = updatedAt ?: Instant.EPOCH
    override val lastPage: List<YouTubeSubscription> get() = pages.last()
    override val hasNextPage: Boolean get() = nextPageToken != null
    val itemSize: Int get() = pages.sumOf { it.size }
    fun update(
        items: List<YouTubeSubscription>,
        nextPageToken: String?,
        updatedAt: Instant? = null,
    ): PagedSubscription = copy(
        pages = pages + listOf(items),
        nextPageToken = nextPageToken,
        updatedAt = updatedAt,
    )
}

private class YouTubePlaylistNotFound(
    override val id: YouTubePlaylist.Id,
) : YouTubePlaylist {
    override val title: String get() = ""
    override val thumbnailUrl: String get() = ""
}
