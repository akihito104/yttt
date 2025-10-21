package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveStream
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject

internal class FindLiveVideoDetailFromTwitchUseCase @Inject constructor(
    private val dataSource: TwitchStreamDataSource.Extended,
) : FindLiveVideoDetailUseCase {
    override suspend operator fun invoke(id: LiveVideo.Id): Result<LiveVideoDetail?> {
        val twitchVideoId = when (id.type) {
            TwitchStream.Id::class -> id.mapTo<TwitchStream.Id>()
            TwitchChannelSchedule.Stream.Id::class -> id.mapTo<TwitchChannelSchedule.Stream.Id>()
            else -> throw AssertionError("unsupported type: ${id.type}")
        }
        val detail = dataSource.fetchStreamDetail(twitchVideoId)
        return Result.success(
            detail?.let {
                val description = when (it) {
                    is TwitchLiveStream -> "${it.gameName} tag: ${it.tags.joinToString()}"
                    is TwitchLiveSchedule -> ""
                    else -> error("unknown item type: $it")
                }
                LiveVideoDetailTwitch(
                    it,
                    AnnotatableString.createForTwitch(it.title),
                    AnnotatableString.createForTwitch(description),
                )
            },
        )
    }
}

internal fun AnnotatableString.Companion.createForTwitch(annotatable: String?): AnnotatableString =
    create(annotatable ?: "") {
        listOf("https://twitch.tv/${it.substring(1)}")
    }

internal class LiveVideoDetailTwitch(
    private val video: TwitchLiveVideo<out TwitchVideo.TwitchVideoId>,
    override val title: AnnotatableString,
    override val description: AnnotatableString,
) : LiveVideoDetail {
    override val id: LiveVideo.Id get() = video.id.mapTo()
    override val channel: LiveChannel get() = video.user.toLiveChannel()
    override val thumbnailUrl: String get() = video.getThumbnailUrl()
    override val isLandscape: Boolean
        get() = when (video) {
            is TwitchLiveSchedule -> false
            else -> true
        }
    override val dateTime: Instant?
        get() = when (video) {
            is TwitchLiveStream -> video.startedAt
            is TwitchLiveSchedule -> video.schedule.startTime
            else -> null
        }
    override val viewerCount: BigInteger?
        get() = when (video) {
            is TwitchLiveStream -> BigInteger.valueOf(video.viewCount.toLong())
            else -> null
        }
    override val contentType: TimetablePage
        get() = when (video) {
            is TwitchLiveStream -> TimetablePage.OnAir
            is TwitchLiveSchedule -> TimetablePage.Upcoming
            else -> error("unsupported type: ${video::class}")
        }
}

internal fun TwitchUserDetail.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = id.mapTo(),
    title = displayName,
    iconUrl = profileImageUrl,
    platform = Twitch,
)
