package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveLocalDataSource @Inject constructor() : YoutubeLiveDataSource {
    private val _subscriptions = MutableStateFlow<Map<LiveSubscription.Id, LiveSubscription>>(
        emptyMap()
    )
    private val _channelLogs: MutableStateFlow<Map<LiveChannel.Id, List<LiveChannelLog>>> =
        MutableStateFlow(emptyMap())
    private val _videos: MutableStateFlow<Map<LiveVideo.Id, LiveVideo>> =
        MutableStateFlow(emptyMap())
    val videos: Flow<List<LiveVideo>> = _videos.map { it.values.toList() }

    override suspend fun fetchAllSubscribe(maxResult: Long): List<LiveSubscription> {
        return _subscriptions.value.values.toList()
    }

    fun addSubscribes(subscriptions: Collection<LiveSubscription>) {
        _subscriptions.update { prev ->
            prev.toMutableMap().apply {
                this.putAll(subscriptions.map { it.id to it })
            }
        }
    }

    fun removeSubscribes(subscriptions: Collection<LiveSubscription.Id>) {
        _subscriptions.update { prev ->
            prev.toMutableMap().apply { subscriptions.forEach { this.remove(it) } }
        }
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant,
        maxResult: Long
    ): List<LiveChannelLog> {
        return _channelLogs.value[channelId] ?: emptyList()
    }

    fun addLiveChannelLogs(channelLogs: List<LiveChannelLog>) {
        val group = channelLogs.groupBy { it.channelId }
        _channelLogs.update {
            val res = it.toMutableMap()
            group.entries.forEach { e ->
                val next = res[e.key]?.toMutableList() ?: mutableListOf()
                res[e.key] = next.apply { addAll(e.value) }
            }
            res
        }
    }

    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        return ids.mapNotNull { _videos.value[it] }
    }

    fun addVideo(video: Collection<LiveVideo>) {
        _videos.update {
            val res = it.toMutableMap()
            video.forEach { v ->
                res[v.id] = v
            }
            res
        }
    }
}
