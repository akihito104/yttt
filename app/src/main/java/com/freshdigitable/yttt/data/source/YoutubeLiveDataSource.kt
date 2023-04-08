package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import java.time.Instant

interface YoutubeLiveDataSource {
    suspend fun fetchAllSubscribe(maxResult: Long = 30): List<LiveSubscription>
    suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant? = null,
        maxResult: Long? = null,
    ): List<LiveChannelLog>

    suspend fun fetchVideoList(
        ids: Collection<LiveVideo.Id>,
    ): List<LiveVideo>
}
