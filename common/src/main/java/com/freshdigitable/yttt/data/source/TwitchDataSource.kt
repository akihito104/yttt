package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.model.Updatable

interface TwitchDataSource : TwitchUserDataSource, TwitchStreamDataSource, TwitchScheduleDataSource {
    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>>
    suspend fun fetchVideosByUserId(id: TwitchUser.Id, itemCount: Int = 20): Result<List<Updatable<TwitchVideoDetail>>>

    interface Local :
        TwitchDataSource,
        TwitchUserDataSource.Local,
        TwitchStreamDataSource.Local,
        TwitchScheduleDataSource.Local,
        ImageDataSource {
        suspend fun upsertCategory(category: Collection<TwitchCategory>)
    }

    interface Extended :
        TwitchUserDataSource.Extended,
        TwitchStreamDataSource.Extended,
        TwitchScheduleDataSource.Extended {
        suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>)
        suspend fun deleteAllTables()
    }

    interface Remote :
        TwitchDataSource,
        TwitchUserDataSource.Remote,
        TwitchStreamDataSource.Remote,
        TwitchScheduleDataSource.Remote
}

interface TwitchUserDataSource {
    suspend fun findUsersById(ids: Set<TwitchUser.Id>? = null): Result<List<Updatable<TwitchUserDetail>>>
    suspend fun fetchMe(): Result<Updatable<TwitchUserDetail>?>
    suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<Updatable<TwitchFollowings>>
    interface Local : TwitchUserDataSource {
        suspend fun addUsers(users: Collection<Updatable<TwitchUserDetail>>)
        suspend fun setMe(me: Updatable<TwitchUserDetail>)
        suspend fun replaceAllFollowings(followings: Updatable<TwitchFollowings>)
    }

    interface Extended

    interface Remote : TwitchUserDataSource
}

interface TwitchStreamDataSource {
    suspend fun fetchFollowedStreams(me: TwitchUser.Id? = null): Result<Updatable<TwitchStreams>?>
    interface Local : TwitchStreamDataSource
    interface Extended {
        suspend fun replaceFollowedStreams(followedStreams: Updatable<TwitchStreams.Updated>)
        suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchLiveVideo<out TwitchVideo.TwitchVideoId>?
    }

    interface Remote : TwitchStreamDataSource
}

interface TwitchScheduleDataSource {
    suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int = 10,
    ): Result<Updatable<TwitchChannelSchedule?>>

    interface Local : TwitchScheduleDataSource
    interface Extended {
        suspend fun setFollowedStreamScheduleBatch(schedules: Map<TwitchUser.Id, Updatable<TwitchChannelSchedule?>>)
    }

    interface Remote : TwitchScheduleDataSource
}
