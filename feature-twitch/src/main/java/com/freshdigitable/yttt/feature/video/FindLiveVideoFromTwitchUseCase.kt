package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveVideoDetail
import javax.inject.Inject

internal class FindLiveVideoFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FindLiveVideoUseCase {
    override suspend operator fun invoke(id: LiveVideo.Id): LiveVideo? {
        val twitchVideoId = when (id.type) {
            TwitchStream.Id::class -> id.mapTo<TwitchStream.Id>()
            TwitchChannelSchedule.Stream.Id::class -> id.mapTo<TwitchChannelSchedule.Stream.Id>()
            else -> throw AssertionError("unsupported type: ${id.type}")
        }
        val d = repository.fetchStreamDetail(twitchVideoId) ?: return null
        val u = (d.user as? TwitchUserDetail) ?: repository.findUsersById(listOf(d.user.id)).first()
        return d.toLiveVideoDetail(u)
    }
}
