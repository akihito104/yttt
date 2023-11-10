package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.time.Instant

interface YoutubeDataSource {
    suspend fun fetchAllSubscribe(maxResult: Long = 30): List<YouTubeSubscription>
    suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): List<YouTubeChannelLog>

    suspend fun fetchVideoList(
        ids: Collection<YouTubeVideo.Id>,
    ): List<YouTubeVideo>

    suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>)
    suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>)
}
