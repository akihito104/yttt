package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoForDetail
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.create
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
        val u = (d.user as? TwitchUserDetail) ?: repository.findUsersById(setOf(d.user.id)).first()
        return LiveVideo.create(d, u)
    }
}

internal class FindLiveVideoDetailAnnotatedFromTwitchUseCase @Inject constructor(
    private val findLiveVideoUseCase: FindLiveVideoFromTwitchUseCase
) : FindLiveVideoDetailAnnotatedUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideoForDetail? {
        val v = findLiveVideoUseCase(id) ?: return null
        return TwitchLiveVideoForDetail(v)
    }
}

internal data class TwitchLiveVideoForDetail(
    override val video: LiveVideo,
) : LiveVideoForDetail {
    override val annotatableTitle: AnnotatableString
        get() = AnnotatableString.createForTwitch(video.title)
    override val annotatableDescription: AnnotatableString
        get() = AnnotatableString.createForTwitch(video.description)
}

internal fun AnnotatableString.Companion.createForTwitch(annotatable: String?): AnnotatableString =
    create(annotatable ?: "") {
        listOf("https://twitch.tv/${it.substring(1)}")
    }
