package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClientImpl.Companion.fetch
import com.google.api.client.http.HttpHeaders
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Playlist
import com.google.api.services.youtube.model.PlaylistItem
import com.google.api.services.youtube.model.PlaylistItemListResponse
import com.google.api.services.youtube.model.PlaylistListResponse
import java.time.Instant

interface YouTubePlaylistClient {
    fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>>
    fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String? = null,
    ): NetworkResponse<List<YouTubePlaylistItem>>

    fun fetchPlaylistItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItemDetail>>
}

internal class YouTubePlaylistClientImpl(private val youtube: YouTube) : YouTubePlaylistClient {
    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> =
        youtube.fetch(YouTubePlaylistRemote.factory) {
            playlists()
                .list(listOf(YouTubeClient.PART_SNIPPET, YouTubeClient.PART_CONTENT_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): NetworkResponse<List<YouTubePlaylistItem>> = youtube.fetchPlaylistItem(
        id,
        maxResult,
        PlaylistItemRemote.factory(id),
        PlaylistItemRemote.part,
        eTag,
    )

    override fun fetchPlaylistItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItemDetail>> = youtube.fetchPlaylistItem(
        id,
        maxResult,
        PlaylistItemDetailRemote.factory,
        PlaylistItemDetailRemote.part,
    )

    private fun <T : YouTubePlaylistItem> YouTube.fetchPlaylistItem(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        factory: ResponseFactory<PlaylistItemListResponse, List<T>>,
        part: List<String>,
        eTag: String? = null,
    ): NetworkResponse<List<T>> = fetch(factory) {
        playlistItems()
            .list(part)
            .setPlaylistId(id.value)
            .setMaxResults(maxResult)
            .apply { eTag?.let { requestHeaders = HttpHeaders().setIfNoneMatch(it) } }
    }
}

private class YouTubePlaylistRemote(
    private val playlist: Playlist,
) : YouTubePlaylist {
    override val id: YouTubePlaylist.Id get() = YouTubePlaylist.Id(playlist.id)
    override val title: String get() = playlist.snippet.title
    override val thumbnailUrl: String get() = playlist.snippet.thumbnails.url
    override fun toString(): String = playlist.toPrettyString()

    companion object {
        val factory: ResponseFactory<PlaylistListResponse, List<YouTubePlaylist>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { YouTubePlaylistRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
    }
}

private class PlaylistItemRemote(
    private val item: PlaylistItem,
    override val playlistId: YouTubePlaylist.Id,
) : YouTubePlaylistItem {
    override val id: YouTubePlaylistItem.Id get() = YouTubePlaylistItem.Id(item.id)
    override val videoId: YouTubeVideo.Id get() = YouTubeVideo.Id(item.contentDetails.videoId)
    override val publishedAt: Instant get() = item.contentDetails.videoPublishedAt.toInstant()

    companion object {
        val factory: (YouTubePlaylist.Id) -> ResponseFactory<PlaylistItemListResponse, List<YouTubePlaylistItem>> =
            { responseFactory(it) }
        val part = listOf(YouTubeClient.PART_CONTENT_DETAILS)

        private fun responseFactory(
            id: YouTubePlaylist.Id,
        ): ResponseFactory<PlaylistItemListResponse, List<YouTubePlaylistItem>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { PlaylistItemRemote(item = it, playlistId = id) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
                eTag = res.etag,
            )
        }
    }
}

private class PlaylistItemDetailRemote(
    private val item: PlaylistItem,
) : YouTubePlaylistItemDetail {
    override val id: YouTubePlaylistItem.Id get() = YouTubePlaylistItem.Id(item.id)
    override val playlistId: YouTubePlaylist.Id get() = YouTubePlaylist.Id(item.snippet.playlistId)
    override val title: String get() = item.snippet.title
    override val thumbnailUrl: String get() = item.snippet.thumbnails?.url ?: ""
    override val videoId: YouTubeVideo.Id get() = YouTubeVideo.Id(item.snippet.resourceId.videoId)
    override val channel: YouTubeChannelTitle
        get() = YouTubeChannelTitleImpl(
            id = YouTubeChannel.Id(item.snippet.channelId),
            title = item.snippet.channelTitle,
        )
    override val description: String get() = item.snippet.description
    override val videoOwnerChannelId: YouTubeChannel.Id?
        get() = item.snippet.videoOwnerChannelId?.let { YouTubeChannel.Id(it) }
    override val publishedAt: Instant get() = item.snippet.publishedAt.toInstant()
    override fun toString(): String = item.toPrettyString()

    companion object {
        val factory: ResponseFactory<PlaylistItemListResponse, List<YouTubePlaylistItemDetail>> = { res, cc ->
            NetworkResponse.create(
                item = res.items.map { PlaylistItemDetailRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }
        val part = listOf(YouTubeClient.PART_SNIPPET)
    }
}
