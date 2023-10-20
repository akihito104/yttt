package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchStreamSchedule
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.mapTo
import javax.inject.Inject

class FindLiveVideoFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo? { // TODO: id type mismatch
        check(id.platform == LivePlatform.TWITCH)
        val d = repository.fetchStreamDetail(id.mapTo<TwitchStream.Id>())
            ?: repository.fetchStreamDetail(id.mapTo<TwitchChannelSchedule.Stream.Id>())
        checkNotNull(d)
        val u = repository.findUsersById(listOf(d.user.id)).first()
        return (d as? TwitchStream)?.toLiveVideo(u)
            ?: (d as? TwitchStreamSchedule)?.toLiveVideo(u)
    }
}

fun TwitchStream.toLiveVideo(user: TwitchUserDetail): LiveVideo {
    return LiveVideoEntity(
        id = LiveVideo.Id(id.value, id.platform),
        channel = user.toLiveChannel(),
        title = title,
        scheduledStartDateTime = startedAt,
        actualStartDateTime = startedAt,
        thumbnailUrl = getThumbnailUrl(),
    )
}

fun TwitchStreamSchedule.toLiveVideo(user: TwitchUserDetail): LiveVideo {
    return LiveVideoEntity(
        id = LiveVideo.Id(id.value, id.platform),
        channel = user.toLiveChannel(),
        scheduledStartDateTime = schedule.startTime,
        scheduledEndDateTime = schedule.endTime,
        title = title,
        thumbnailUrl = getThumbnailUrl(),
    )
}

fun TwitchUserDetail.toLiveChannel(): LiveChannel {
    return LiveChannelEntity(
        id = LiveChannel.Id(id.value, id.platform),
        title = displayName,
        iconUrl = profileImageUrl,
    )
}
