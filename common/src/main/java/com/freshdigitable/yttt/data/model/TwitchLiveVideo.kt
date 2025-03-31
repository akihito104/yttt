package com.freshdigitable.yttt.data.model

interface TwitchLiveVideo<T : TwitchVideo.TwitchVideoId> : TwitchVideo<T> {
    override val user: TwitchUserDetail
}

interface TwitchLiveStream : TwitchStream, TwitchLiveVideo<TwitchStream.Id>
interface TwitchLiveSchedule : TwitchLiveVideo<TwitchChannelSchedule.Stream.Id> {
    val schedule: TwitchChannelSchedule.Stream

    companion object {
        fun create(
            user: TwitchUserDetail,
            schedule: TwitchChannelSchedule.Stream,
        ): TwitchLiveSchedule = Impl(user, schedule)
    }

    private data class Impl(
        override val user: TwitchUserDetail,
        override val schedule: TwitchChannelSchedule.Stream,
    ) : TwitchLiveSchedule {
        override val id: TwitchChannelSchedule.Stream.Id get() = schedule.id
        override val title: String get() = schedule.title
        override val url: String get() = "https://twitch.tv/${user.loginName}/schedule?seriesID=${id.value}"
        override val thumbnailUrlBase: String = ""
        override val viewCount: Int = 0
        override val language: String = ""

        override fun getThumbnailUrl(width: Int, height: Int): String = ""
    }
}

interface TwitchLiveChannelSchedule : TwitchChannelSchedule {
    override val broadcaster: TwitchUserDetail
}
