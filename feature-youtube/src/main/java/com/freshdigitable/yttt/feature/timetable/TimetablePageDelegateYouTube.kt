package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideo.Upcoming.Companion.scheduledStartLocalDateWithOffset
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import com.freshdigitable.yttt.data.model.toLiveVideo
import com.freshdigitable.yttt.feature.timetable.YouTubeUpcomingLiveVideo.Companion.isStreamTodayOnwards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

internal class FetchYouTubeOnAirItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> = repository.videos.map { v ->
        v.filter { it.isNowOnAir() }.map { it.toLiveVideo() }
    }
}

internal class FetchYouTubeUpcomingItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
    private val settingRepository: SettingRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> {
        val video = repository.videos.map { v ->
            v.filter { it.isUpcoming() && it.isFreeChat != true && it.scheduledStartDateTime != null }
        }.distinctUntilChanged()
        val changeDateTime = settingRepository.changeDateTime
        return combine(video, changeDateTime) { v, c ->
            val current = LocalDateTime.now()
            v.map { YouTubeUpcomingLiveVideo(it, c) }
                .filter { it.isStreamTodayOnwards(current) }
        }
    }
}

internal class FetchYouTubeFreeChatItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isFreeChat == true }.map { it.toLiveVideo() } }
}

internal data class YouTubeUpcomingLiveVideo(
    private val video: YouTubeVideoExtended,
    private val changeDateTime: Int?,
) : LiveVideo.Upcoming {
    override val offset: Duration
        get() = Duration.ofHours(((changeDateTime ?: 24) - 24).toLong())
    override val scheduledStartDateTime: Instant
        get() = checkNotNull(video.scheduledStartDateTime)
    override val channel: LiveChannel
        get() = video.channel.toLiveChannel()
    override val scheduledEndDateTime: Instant?
        get() = video.scheduledEndDateTime
    override val actualStartDateTime: Instant?
        get() = video.actualStartDateTime
    override val actualEndDateTime: Instant?
        get() = video.actualEndDateTime
    override val url: String
        get() = video.url
    override val id: LiveVideo.Id
        get() = video.id.mapTo()
    override val title: String
        get() = video.title
    override val thumbnailUrl: String
        get() = video.thumbnailUrl

    companion object {
        internal fun YouTubeUpcomingLiveVideo.isStreamTodayOnwards(current: LocalDateTime): Boolean =
            (current - offset).toLocalDate() <= scheduledStartLocalDateWithOffset()
    }
}
