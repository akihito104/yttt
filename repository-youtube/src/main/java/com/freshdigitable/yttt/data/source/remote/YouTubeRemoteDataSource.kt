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
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.update
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.recoverFromNotFoundError
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

    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> =
        fetchList(ids) { fetchPlaylist(it) }

    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItems>> = fetch {
        val items = fetchPlaylistItems(id, maxResult, cache?.eTag)
        if (cache != null) {
            cache.update(items)
        } else {
            val playlist = fetchPlaylist(setOf(id)).map { it.first() }
            YouTubePlaylistWithItem.newPlaylist(playlist = playlist, items = items)
        }
    }.recoverFromNotFoundError { cacheControl ->
        cache?.update(emptyList<YouTubePlaylistItem>().toUpdatable(cacheControl))
            ?: YouTubePlaylistWithItem.newPlaylist(
                playlist = YouTubePlaylistNotFound(id).toUpdatable(cacheControl),
                items = Updatable.create(emptyList(), cacheControl),
            )
    }

    override suspend fun fetchPlaylistWithItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>> = fetch {
        val items = fetchPlaylistItemDetails(id, maxResult)
        if (cache != null) {
            cache.update(items)
        } else {
            val playlist = fetchPlaylist(setOf(id)).map { it.first() }
            YouTubePlaylistWithItem.newPlaylist(playlist = playlist, items = items)
        }
    }.recoverFromNotFoundError { cacheControl ->
        cache?.update(emptyList<YouTubePlaylistItemDetail>().toUpdatable(cacheControl))
            ?: YouTubePlaylistWithItem.newPlaylist(
                playlist = YouTubePlaylistNotFound(id).toUpdatable(cacheControl),
                items = Updatable.create(emptyList(), cacheControl),
            )
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
