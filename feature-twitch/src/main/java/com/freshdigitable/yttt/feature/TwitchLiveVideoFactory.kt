package com.freshdigitable.yttt.feature

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveStream
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.mapTo
import java.math.BigInteger
import java.time.Instant

internal fun LiveVideo.Companion.create(video: TwitchLiveVideo<*>): LiveVideo<*> = when (video) {
    is TwitchLiveStream -> TwitchOnAirLiveVideo(video)
    is TwitchLiveSchedule -> TwitchUpcomingLiveVideo(video)
    else -> throw AssertionError("unsupported type: ${this::class.simpleName}")
}

internal data class TwitchOnAirLiveVideo(
    private val stream: TwitchLiveStream,
) : LiveVideo.OnAir {
    override val actualStartDateTime: Instant get() = stream.startedAt
    override val channel: LiveChannel get() = stream.user.toLiveChannel()
    override val scheduledStartDateTime: Instant? get() = null
    override val scheduledEndDateTime: Instant? get() = null
    override val actualEndDateTime: Instant? get() = null
    override val id: LiveVideo.Id get() = stream.id.mapTo()
    override val title: String get() = stream.title
    override val thumbnailUrl: String get() = stream.getThumbnailUrl(width = 640, height = 360)
    override val description: String get() = "${stream.gameName} tag: ${stream.tags.joinToString()}"
    override val viewerCount: BigInteger? get() = BigInteger.valueOf(stream.viewCount.toLong())
}

internal data class TwitchUpcomingLiveVideo(
    private val schedule: TwitchLiveSchedule,
) : LiveVideo.Upcoming {
    override val id: LiveVideo.Id get() = schedule.id.mapTo()
    override val scheduledStartDateTime: Instant get() = schedule.schedule.startTime
    override val channel: LiveChannel get() = schedule.user.toLiveChannel()
    override val scheduledEndDateTime: Instant? get() = schedule.schedule.endTime
    override val actualStartDateTime: Instant? get() = null
    override val actualEndDateTime: Instant? get() = null
    override val title: String get() = schedule.title
    override val thumbnailUrl: String get() = schedule.getThumbnailUrl(width = 240, height = 360)
    override val isLandscape: Boolean get() = false
    override val description: String get() = ""
    override val viewerCount: BigInteger? get() = null
}

internal fun TwitchUserDetail.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = id.mapTo(),
    title = displayName,
    iconUrl = profileImageUrl,
    platform = Twitch,
)
