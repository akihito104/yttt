package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import java.math.BigInteger

inline fun <reified T : IdBase> IdBase.mapTo(): T {
    (this as? LiveId)?.checkMappable<T>()
    return when (T::class) {
        YouTubeVideo.Id::class -> YouTubeVideo.Id(value) as T
        YouTubeChannel.Id::class -> YouTubeChannel.Id(value) as T
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class -> TwitchChannelSchedule.Stream.Id(value) as T
        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T
        LiveSubscription.Id::class -> LiveSubscription.Id(value, this::class) as T
        LiveVideo.Id::class -> LiveVideo.Id(value, this::class) as T
        LiveChannel.Id::class -> LiveChannel.Id(value, this::class) as T
        else -> throw AssertionError("unsupported id type: $this")
    }
}

inline fun <reified T : IdBase> IdBase.checkMappable() {
    if (this is LiveId) {
        check(this.type == T::class) { "unmappable: ${this.type} to ${T::class}" }
    }
}

fun TwitchUserDetail.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = id.mapTo(),
    title = displayName,
    iconUrl = profileImageUrl,
)

fun YouTubeChannel.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = id.mapTo(),
    title = title,
    iconUrl = iconUrl,
)

fun TwitchUserDetail.toLiveChannelDetail(): LiveChannelDetail = LiveChannelDetailEntity(
    id = id.mapTo(),
    title = this.displayName,
    iconUrl = this.profileImageUrl,
    bannerUrl = "",
    customUrl = loginName,
    description = description,
    isSubscriberHidden = false,
    keywords = emptyList(),
    publishedAt = this.createdAt,
    subscriberCount = BigInteger.ZERO,
    uploadedPlayList = null,
    videoCount = BigInteger.ZERO,
    viewsCount = BigInteger.valueOf(this.viewsCount.toLong()),
)

fun YouTubeChannelDetail.toLiveChannelDetail(): LiveChannelDetail = LiveChannelDetailEntity(
    id = id.mapTo(),
    title = title,
    videoCount = viewsCount,
    isSubscriberHidden = isSubscriberHidden,
    keywords = keywords,
    subscriberCount = subscriberCount,
    uploadedPlayList = uploadedPlayList,
    bannerUrl = bannerUrl,
    customUrl = customUrl,
    description = description,
    viewsCount = viewsCount,
    publishedAt = publishedAt,
    iconUrl = iconUrl,
)

fun TwitchStream.toLiveVideo(user: TwitchUserDetail): LiveVideo = LiveVideoEntity(
    id = id.mapTo(),
    channel = user.toLiveChannel(),
    title = title,
    scheduledStartDateTime = startedAt,
    actualStartDateTime = startedAt,
    thumbnailUrl = getThumbnailUrl(),
    url = url,
)

fun TwitchStreamSchedule.toLiveVideo(user: TwitchUserDetail): LiveVideo = LiveVideoEntity(
    id = id.mapTo(),
    channel = user.toLiveChannel(),
    scheduledStartDateTime = schedule.startTime,
    scheduledEndDateTime = schedule.endTime,
    title = title,
    thumbnailUrl = getThumbnailUrl(),
    url = url,
)

fun YouTubeVideo.toLiveVideo(): LiveVideo = LiveVideoEntity(
    id = id.mapTo(),
    title = title,
    channel = channel.toLiveChannel(),
    thumbnailUrl = thumbnailUrl,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    url = url,
    isFreeChat = isFreeChat,
)

private data class LiveVideoDetailImpl(
    private val video: LiveVideo,
    override val description: String,
    override val viewerCount: BigInteger? = null,
) : LiveVideoDetail, LiveVideo by video

fun TwitchVideo<*>.toLiveVideoDetail(user: TwitchUserDetail): LiveVideoDetail = when (this) {
    is TwitchStream -> this.toLiveVideoDetail(user)
    is TwitchStreamSchedule -> this.toLiveVideoDetail(user)
    else -> throw AssertionError("unsupported type: ${this::class.simpleName}")
}

fun TwitchStream.toLiveVideoDetail(user: TwitchUserDetail): LiveVideoDetail = LiveVideoDetailImpl(
    video = this.toLiveVideo(user),
    description = "${this.gameName} tag: ${this.tags.joinToString()}",
    viewerCount = BigInteger.valueOf(this.viewCount.toLong())
)

fun TwitchStreamSchedule.toLiveVideoDetail(user: TwitchUserDetail): LiveVideoDetail =
    LiveVideoDetailImpl(
        video = this.toLiveVideo(user),
        description = "",
    )

fun YouTubeVideo.toLiveVideoDetail(): LiveVideoDetail = LiveVideoDetailImpl(
    video = this.toLiveVideo(),
    description = description,
    viewerCount = viewerCount,
)

fun TwitchBroadcaster.toLiveSubscription(order: Int, user: TwitchUserDetail): LiveSubscription =
    LiveSubscriptionEntity(
        id = id.mapTo(),
        channel = user.toLiveChannel(),
        order = order,
        subscribeSince = followedAt,
    )

fun YouTubeSubscription.toLiveSubscription(): LiveSubscription = LiveSubscriptionEntity(
    id = id.mapTo(),
    channel = channel.toLiveChannel(),
    subscribeSince = subscribeSince,
    order = order,
)

fun YouTubePlaylist.toLiveVideoThumbnail(): LiveVideoThumbnail = LiveVideoThumbnailEntity(
    id = id.mapTo(),
    title = title,
    thumbnailUrl = thumbnailUrl,
)

fun YouTubePlaylistItem.toLiveVideoThumbnail(): LiveVideoThumbnail = LiveVideoThumbnailEntity(
    id = id.mapTo(),
    title = title,
    thumbnailUrl = thumbnailUrl,
)
