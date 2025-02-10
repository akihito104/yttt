package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.isUpcomingWithinPublicationDeadline
import com.freshdigitable.yttt.feature.create
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class FetchYouTubeOnAirItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo<*>>> = repository.videos.map { v ->
        v.filter { it.isNowOnAir() }.map { LiveVideo.create(it) }
    }
}

internal class FetchYouTubeUpcomingItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo<*>>> = repository.videos.map { v ->
        val current = dateTimeProvider.now()
        v.filter {
            it.isUpcoming() && it.isFreeChat != true && it.scheduledStartDateTime != null &&
                it.isUpcomingWithinPublicationDeadline(current)
        }.map { LiveVideo.create(it) }
    }
}

internal class FetchYouTubeFreeChatItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo<*>>> = repository.videos.map { v ->
        v.filter { it.isFreeChat == true }.map { LiveVideo.create(it) }
    }
}
