package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelLogEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveRemoteDataSource @Inject constructor(
    accountRepository: AccountRepository,
) : YoutubeLiveDataSource {
    private val youtube = YouTube.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(), accountRepository.credential
    ).build()

    override suspend fun fetchAllSubscribe(
        maxResult: Long,
    ): List<LiveSubscription> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.Subscriptions().list(listOf(PART_SNIPPET))
                    .setMine(true)
                    .setMaxResults(maxResult)
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        ).mapIndexed { i, s -> s.toLiveSubscription(i) }
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant,
        maxResult: Long,
    ): List<LiveChannelLog> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.activities().list(listOf(PART_SNIPPET, PART_CONTENT_DETAILS))
                    .setChannelId(channelId.value)
                    .setMaxResults(maxResult)
                    .setPublishedAfter(publishedAfter.toString())
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        ).filter { it.contentDetails?.upload != null }
            .map { it.toChannelLog() }
    }

    override suspend fun fetchVideoList(
        ids: Collection<LiveVideo.Id>,
    ): List<LiveVideo> = withContext(Dispatchers.IO) {
        ids.map { it.value }.chunked(VIDEO_MAX_FETCH_SIZE).flatMap {
            youtube.videos().list(listOf(PART_SNIPPET, PART_LIVE_STREAMING_DETAILS))
                .setId(it)
                .execute()
                .items
        }.map { it.toLiveVideo() }
    }

    private fun <T, E> fetchAllItems(
        fetcher: (String?) -> T,
        getItems: T.() -> List<E>,
        getNextToken: T.() -> String?,
    ): List<E> {
        var token: String? = null
        val res = mutableListOf<E>()
        do {
            val response = fetcher(token)
            res.addAll(response.getItems())
            token = response.getNextToken()
        } while (token != null)
        return res
    }

    companion object {
        private const val PART_SNIPPET = "snippet"
        private const val PART_CONTENT_DETAILS = "contentDetails"
        private const val PART_LIVE_STREAMING_DETAILS = "liveStreamingDetails"
        // https://developers.google.com/youtube/v3/docs/videos/list#parameters
        private const val VIDEO_MAX_FETCH_SIZE = 50
    }
}

private fun Subscription.toLiveSubscription(order: Int): LiveSubscription = LiveSubscriptionEntity(
    id = LiveSubscription.Id(id),
    subscribeSince = Instant.ofEpochMilli(snippet.publishedAt.value),
    channel = LiveChannelEntity(
        id = LiveChannel.Id(snippet.resourceId.channelId),
        iconUrl = snippet.thumbnails.url,
        title = snippet.title,
    ),
    order = order,
)

private fun Activity.toChannelLog(): LiveChannelLog = LiveChannelLogEntity(
    id = LiveChannelLog.Id(id),
    dateTime = Instant.ofEpochMilli(snippet.publishedAt.value),
    videoId = LiveVideo.Id(contentDetails.upload.videoId),
    channelId = LiveChannel.Id(snippet.channelId),
    thumbnailUrl = snippet.thumbnails.url,
)

private fun Video.toLiveVideo(): LiveVideo = LiveVideoEntity(
    id = LiveVideo.Id(id),
    channel = LiveChannelEntity(
        id = LiveChannel.Id(snippet.channelId),
        title = snippet.channelTitle,
        iconUrl = "",
    ),
    title = snippet.title,
    scheduledStartDateTime = liveStreamingDetails?.scheduledStartTime?.toInstant(),
    scheduledEndDateTime = liveStreamingDetails?.scheduledEndTime?.toInstant(),
    actualStartDateTime = liveStreamingDetails?.actualStartTime?.toInstant(),
    actualEndDateTime = liveStreamingDetails?.actualEndTime?.toInstant(),
    thumbnailUrl = this.snippet.thumbnails.url,
)

private fun DateTime.toInstant(): Instant = Instant.ofEpochMilli(value)
private val ThumbnailDetails.url: String
    get() = (maxres ?: high ?: standard ?: medium ?: default).url ?: ""
