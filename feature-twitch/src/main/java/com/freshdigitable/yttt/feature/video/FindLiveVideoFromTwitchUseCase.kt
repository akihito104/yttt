package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import com.freshdigitable.yttt.feature.create
import javax.inject.Inject

internal class FindLiveVideoFromTwitchUseCase @Inject constructor(
    private val dataSource: TwitchStreamDataSource.Extended,
) : FindLiveVideoUseCase {
    override suspend operator fun invoke(id: LiveVideo.Id): Result<LiveVideo<*>?> {
        val twitchVideoId = when (id.type) {
            TwitchStream.Id::class -> id.mapTo<TwitchStream.Id>()
            TwitchChannelSchedule.Stream.Id::class -> id.mapTo<TwitchChannelSchedule.Stream.Id>()
            else -> throw AssertionError("unsupported type: ${id.type}")
        }
        val d = dataSource.fetchStreamDetail(twitchVideoId)
        return Result.success(d?.let { LiveVideo.create(it) })
    }
}

internal class TwitchAnnotatableStringFactory @Inject constructor() : AnnotatableStringFactory {
    override fun invoke(text: String): AnnotatableString = AnnotatableString.createForTwitch(text)
}

internal fun AnnotatableString.Companion.createForTwitch(annotatable: String?): AnnotatableString =
    create(annotatable ?: "") {
        listOf("https://twitch.tv/${it.substring(1)}")
    }
