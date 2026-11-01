package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.toLiveId
import com.freshdigitable.yttt.di.ClassMap
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.logE
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Instant

internal class FindLiveVideoDetailFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
    annotatableStringFactoryMap: ClassMap<LivePlatform, AnnotatableString.Factory>,
) : FindLiveVideoDetailUseCase {
    private val annotatableStringFactory: AnnotatableString.Factory =
        annotatableStringFactoryMap.getValue(YouTube::class.java)

    override suspend fun invoke(id: LiveVideo.Id): Result<LiveVideoDetail?> {
        check(id.type == YouTubeVideo.Id::class)
        return repository.fetchVideoList(setOf(id.toLiveId()))
            .onFailure { logE(throwable = it) { "invoke: $id" } }
            .onSuccess { repository.addVideo(it) }
            .map { v ->
                v.firstOrNull()?.let {
                    LiveVideoDetailYouTube(
                        it.item,
                        annotatableStringFactory.create(it.item.title),
                        annotatableStringFactory.create(it.item.description),
                    )
                }
            }
    }
}

internal class LiveVideoDetailYouTube(
    private val item: YouTubeVideoExtended,
    override val title: AnnotatableString,
    override val description: AnnotatableString,
) : LiveVideoDetail {
    override val id: LiveVideo.Id get() = item.id.toLiveId()
    override val channel: LiveChannel get() = item.channel.toLiveChannel()
    override val thumbnailUrl: String get() = item.thumbnailUrl
    override val contentType: TimetablePage
        get() = when {
            item.isNowOnAir() -> TimetablePage.OnAir
            item.isFreeChat == true -> TimetablePage.FreeChat
            item.isUpcoming() -> TimetablePage.Upcoming
            else -> error("unknown type: $item")
        }
    override val dateTime: Instant?
        get() = when (contentType) {
            TimetablePage.OnAir -> item.actualStartDateTime
            TimetablePage.Upcoming -> item.scheduledStartDateTime
            else -> null
        }
    override val viewerCount: BigInteger? get() = item.viewerCount
}

internal fun YouTubeChannel.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = id.toLiveId(),
    title = title,
    iconUrl = iconUrl,
    platform = YouTube,
)
