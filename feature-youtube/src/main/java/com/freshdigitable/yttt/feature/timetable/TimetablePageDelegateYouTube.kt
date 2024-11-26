package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.toLiveVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> = repository.videos.map { v ->
        v.filter { it.isUpcoming() && it.isFreeChat != true }
            .filter { it.scheduledStartDateTime != null }
            .map { it.toLiveVideo() }
    }
}

internal class FetchYouTubeFreeChatItemSourceUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchTimetableItemSourceUseCase {
    override fun invoke(): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isFreeChat == true }.map { it.toLiveVideo() } }
}
