package com.freshdigitable.yttt.data.source.remote

import androidx.annotation.VisibleForTesting
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClientImpl.Companion.fetch
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoListResponse
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import java.math.BigInteger
import java.time.Instant

interface YouTubeVideoClient {
    fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>>
}

internal class YouTubeVideoClientImpl(private val youtube: YouTube) : YouTubeVideoClient {
    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> =
        youtube.fetch(YouTubeVideoRemote.factory) {
            videos()
                .list(listOf(YouTubeClient.Companion.PART_SNIPPET, YouTubeClient.Companion.PART_LIVE_STREAMING_DETAILS))
                .setId(ids.map { it.value })
                .setMaxResults(ids.size.toLong())
        }
}

@VisibleForTesting
internal class YouTubeVideoRemote(
    private val video: Video,
) : YouTubeVideo {
    private val liveStreamingDetails: VideoLiveStreamingDetails? get() = video.liveStreamingDetails
    private val snippet get() = requireNotNull(video.snippet) { "json: $video" }
    override val id: YouTubeVideo.Id get() = YouTubeVideo.Id(video.id)
    override val channel: YouTubeChannelTitle
        get() = YouTubeChannelTitleImpl(
            id = YouTubeChannel.Id(snippet.channelId),
            title = snippet.channelTitle,
        )
    override val title: String get() = snippet.title
    override val scheduledStartDateTime: Instant?
        get() = liveStreamingDetails?.scheduledStartTime?.toInstant()
    override val scheduledEndDateTime: Instant?
        get() = liveStreamingDetails?.scheduledEndTime?.toInstant()
    override val actualStartDateTime: Instant? get() = liveStreamingDetails?.actualStartTime?.toInstant()
    override val actualEndDateTime: Instant? get() = liveStreamingDetails?.actualEndTime?.toInstant()
    override val thumbnailUrl: String get() = snippet.thumbnails.url
    override val description: String get() = snippet.description
    override val viewerCount: BigInteger? get() = liveStreamingDetails?.concurrentViewers
    override val liveBroadcastContent: YouTubeVideo.BroadcastType
        get() = findBy(snippet.liveBroadcastContent)

    override fun toString(): String = video.toString()

    companion object {
        val factory: ResponseFactory<VideoListResponse, List<YouTubeVideo>> = { res, cc ->
            NetworkResponse.Companion.create(
                item = res.items.map { YouTubeVideoRemote(it) },
                cacheControl = cc,
                nextPageToken = res.nextPageToken,
            )
        }

        private fun findBy(name: String?): YouTubeVideo.BroadcastType = when (name) {
            "live" -> YouTubeVideo.BroadcastType.LIVE
            "upcoming" -> YouTubeVideo.BroadcastType.UPCOMING
            "none" -> YouTubeVideo.BroadcastType.NONE
            null -> YouTubeVideo.BroadcastType.NONE
            else -> throw NotImplementedError("unknown liveBroadcastContent: $name")
        }
    }
}
