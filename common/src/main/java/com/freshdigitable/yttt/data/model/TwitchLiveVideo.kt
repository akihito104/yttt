package com.freshdigitable.yttt.data.model

interface TwitchLiveVideo<T : TwitchVideo.TwitchVideoId> : TwitchVideo<T> {
    override val user: TwitchUserDetail
}

interface TwitchLiveStream : TwitchStream, TwitchLiveVideo<TwitchStream.Id>
interface TwitchLiveSchedule : TwitchLiveVideo<TwitchChannelSchedule.Stream.Id> {
    val schedule: TwitchChannelSchedule.Stream
    override val id: TwitchChannelSchedule.Stream.Id get() = schedule.id
    override val title: String get() = schedule.title
    override val url: String get() = "https://twitch.tv/${user.loginName}/schedule?seriesID=${id.value}"
    override val viewCount: Int get() = 0
    override val language: String get() = ""
    override fun getThumbnailUrl(width: Int, height: Int): String {
        return if (thumbnailUrlBase.isEmpty()) {
            ""
        } else {
            super.getThumbnailUrl(width, height)
        }
    }

    companion object {
        fun create(
            user: TwitchUserDetail,
            schedule: TwitchChannelSchedule.Stream,
            thumbnailUrlBase: String? = schedule.category?.artUrlBase,
        ): TwitchLiveSchedule = Impl(user, schedule, thumbnailUrlBase ?: "")
    }

    private data class Impl(
        override val user: TwitchUserDetail,
        override val schedule: TwitchChannelSchedule.Stream,
        override val thumbnailUrlBase: String = "",
    ) : TwitchLiveSchedule
}

interface TwitchLiveChannelSchedule : TwitchChannelSchedule {
    override val broadcaster: TwitchUserDetail
}
