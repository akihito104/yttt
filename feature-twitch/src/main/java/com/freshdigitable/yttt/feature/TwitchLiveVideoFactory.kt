package com.freshdigitable.yttt.feature

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreamSchedule
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import java.math.BigInteger
import java.time.Instant

internal fun LiveVideo.Companion.create(
    video: TwitchVideo<*>,
    user: TwitchUserDetail,
): LiveVideo<*> = when (video) {
    is TwitchStream -> TwitchOnAirLiveVideo(video, user)
    is TwitchStreamSchedule -> TwitchUpcomingLiveVideo(video, user)
    else -> throw AssertionError("unsupported type: ${this::class.simpleName}")
}

internal data class TwitchOnAirLiveVideo(
    private val stream: TwitchStream,
    private val user: TwitchUserDetail,
) : LiveVideo.OnAir {
    override val actualStartDateTime: Instant
        get() = stream.startedAt
    override val channel: LiveChannel
        get() = user.toLiveChannel()
    override val scheduledStartDateTime: Instant?
        get() = null
    override val scheduledEndDateTime: Instant?
        get() = null
    override val actualEndDateTime: Instant?
        get() = null
    override val url: String
        get() = stream.url
    override val id: LiveVideo.Id
        get() = stream.id.mapTo()
    override val title: String
        get() = stream.title
    override val thumbnailUrl: String
        get() = stream.getThumbnailUrl()
    override val description: String
        get() = "${stream.gameName} tag: ${stream.tags.joinToString()}"
    override val viewerCount: BigInteger?
        get() = BigInteger.valueOf(stream.viewCount.toLong())
}

internal data class TwitchUpcomingLiveVideo(
    private val stream: TwitchStreamSchedule,
    private val user: TwitchUserDetail,
) : LiveVideo.Upcoming {
    override val id: LiveVideo.Id
        get() = stream.id.mapTo()
    override val scheduledStartDateTime: Instant
        get() = stream.schedule.startTime
    override val channel: LiveChannel
        get() = user.toLiveChannel()
    override val scheduledEndDateTime: Instant?
        get() = stream.schedule.endTime
    override val actualStartDateTime: Instant?
        get() = null
    override val actualEndDateTime: Instant?
        get() = null
    override val url: String
        get() = stream.url
    override val title: String
        get() = stream.title
    override val thumbnailUrl: String
        get() = stream.getThumbnailUrl()
    override val description: String
        get() = ""
    override val viewerCount: BigInteger?
        get() = null
}
