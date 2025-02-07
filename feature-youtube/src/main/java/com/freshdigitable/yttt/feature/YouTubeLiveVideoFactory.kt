package com.freshdigitable.yttt.feature

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import java.math.BigInteger
import java.time.Instant

internal fun LiveVideo.Companion.create(video: YouTubeVideoExtended): LiveVideo<*> = when {
    video.isNowOnAir() -> YouTubeOnAirLiveVideo(video)
    video.isFreeChat == true -> YouTubeFreeChatLiveVideo(video)
    video.isUpcoming() -> YouTubeUpcomingLiveVideo(video)
    else -> YouTubeLiveVideoEntity(video)
}

internal data class YouTubeUpcomingLiveVideo(
    private val video: YouTubeVideoExtended,
) : YouTubeLiveVideo<LiveVideo.Upcoming>(video), LiveVideo.Upcoming {
    init {
        check(video.isUpcoming())
        check(video.isFreeChat != true)
    }

    override val scheduledStartDateTime: Instant
        get() = checkNotNull(video.scheduledStartDateTime)
}

internal data class YouTubeOnAirLiveVideo(
    private val video: YouTubeVideoExtended,
) : YouTubeLiveVideo<LiveVideo.OnAir>(video), LiveVideo.OnAir {
    init {
        check(video.isNowOnAir())
    }

    override val actualStartDateTime: Instant
        get() = checkNotNull(video.actualStartDateTime)
}

internal data class YouTubeFreeChatLiveVideo(
    private val video: YouTubeVideoExtended,
) : YouTubeLiveVideo<LiveVideo.FreeChat>(video), LiveVideo.FreeChat {
    init {
        check(video.isFreeChat == true)
        checkNotNull(video.scheduledStartDateTime)
    }

    override val scheduledStartDateTime: Instant
        get() = checkNotNull(video.scheduledStartDateTime)
}

internal data class YouTubeLiveVideoEntity(
    private val video: YouTubeVideoExtended,
) : YouTubeLiveVideo<YouTubeLiveVideoEntity>(video)

internal abstract class YouTubeLiveVideo<T : LiveVideo<T>>(
    private val video: YouTubeVideoExtended,
) : LiveVideo<T> {
    override val channel: LiveChannel
        get() = video.channel.toLiveChannel()
    override val scheduledStartDateTime: Instant?
        get() = video.scheduledStartDateTime
    override val scheduledEndDateTime: Instant?
        get() = video.scheduledEndDateTime
    override val actualStartDateTime: Instant?
        get() = video.actualStartDateTime
    override val actualEndDateTime: Instant?
        get() = video.scheduledEndDateTime
    override val url: String
        get() = video.url
    override val description: String
        get() = video.description
    override val viewerCount: BigInteger?
        get() = video.viewerCount
    override val id: LiveVideo.Id
        get() = video.id.mapTo()
    override val title: String
        get() = video.title
    override val thumbnailUrl: String
        get() = video.thumbnailUrl
}
