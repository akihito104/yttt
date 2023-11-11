package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import java.math.BigInteger

inline fun <reified T : IdBase<String>> IdBase<String>.mapTo(): T {
    return when (T::class) {
        YouTubeVideo.Id::class -> YouTubeVideo.Id(value) as T
        YouTubeChannel.Id::class -> YouTubeChannel.Id(value) as T
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class -> TwitchChannelSchedule.Stream.Id(value) as T
        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T
        LiveSubscription.Id::class -> LiveSubscription.Id(value, platform) as T
        LiveVideo.Id::class -> LiveVideo.Id(value, platform) as T
        LiveChannel.Id::class -> LiveChannel.Id(value, platform) as T
        else -> throw AssertionError("unsupported id type: $this")
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
