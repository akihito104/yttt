package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreamSchedule
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveVideo
import javax.inject.Inject

interface FindLiveVideoUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo?
}

class FindLiveVideoFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FindLiveVideoUseCase {
    override suspend operator fun invoke(id: LiveVideo.Id): LiveVideo? { // TODO: id type mismatch
        check(id.platform == LivePlatform.TWITCH)
        val d = repository.fetchStreamDetail(id.mapTo<TwitchStream.Id>())
            ?: repository.fetchStreamDetail(id.mapTo<TwitchChannelSchedule.Stream.Id>())
        checkNotNull(d)
        val u = repository.findUsersById(listOf(d.user.id)).first()
        return (d as? TwitchStream)?.toLiveVideo(u)
            ?: (d as? TwitchStreamSchedule)?.toLiveVideo(u)
    }
}

class FindLiveVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FindLiveVideoUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideo? {
        val v = repository.fetchVideoDetail(id.mapTo())
        return v?.toLiveVideo()
    }
}
