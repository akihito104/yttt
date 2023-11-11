package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreamSchedule
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.mapTo
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap
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

fun TwitchStream.toLiveVideo(user: TwitchUserDetail): LiveVideo {
    return LiveVideoEntity(
        id = id.mapTo(),
        channel = user.toLiveChannel(),
        title = title,
        scheduledStartDateTime = startedAt,
        actualStartDateTime = startedAt,
        thumbnailUrl = getThumbnailUrl(),
        url = url,
    )
}

fun TwitchStreamSchedule.toLiveVideo(user: TwitchUserDetail): LiveVideo {
    return LiveVideoEntity(
        id = id.mapTo(),
        channel = user.toLiveChannel(),
        scheduledStartDateTime = schedule.startTime,
        scheduledEndDateTime = schedule.endTime,
        title = title,
        thumbnailUrl = getThumbnailUrl(),
        url = url,
    )
}

fun TwitchUserDetail.toLiveChannel(): LiveChannel {
    return LiveChannelEntity(
        id = id.mapTo(),
        title = displayName,
        iconUrl = profileImageUrl,
    )
}

class FindLiveVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FindLiveVideoUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideo? {
        val v = repository.fetchVideoDetail(id.mapTo())
        return v?.toLiveVideo()
    }
}

@MapKey
annotation class PlatformKey(val platform: LivePlatform)

@Module
@InstallIn(ViewModelComponent::class)
interface FindLiveVideoModule {
    @Binds
    @IntoMap
    @PlatformKey(LivePlatform.TWITCH)
    fun bindFindLiveVideoFromTwitchUseCase(useCase: FindLiveVideoFromTwitchUseCase): FindLiveVideoUseCase

    @Binds
    @IntoMap
    @PlatformKey(LivePlatform.YOUTUBE)
    fun bindFindLiveVideoFromYouTubeUseCase(useCase: FindLiveVideoFromYouTubeUseCase): FindLiveVideoUseCase
}
