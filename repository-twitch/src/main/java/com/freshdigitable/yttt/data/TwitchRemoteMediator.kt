package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.PagingSourceFunction
import com.freshdigitable.yttt.data.source.local.db.TwitchPagingSource
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.logE
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
internal class TwitchRemoteMediator @Inject constructor(
    private val pagingSource: TwitchPagingSource,
    private val repository: TwitchRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, LiveSubscription>() {
    override suspend fun initialize(): InitializeAction {
        if (pagingSource.isUpdatable(dateTimeProvider.now())) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, LiveSubscription>,
    ): MediatorResult {
        logD { "load: $loadType, $state" }
        val me = repository.fetchMe()
            .onFailure { logE(throwable = it) { "load:" } }
            .getOrNull() ?: return MediatorResult.Success(endOfPaginationReached = true)
        return when (loadType) {
            LoadType.REFRESH -> {
                repository.fetchAllFollowings(me.id).map { followings ->
                    val userId = followings.followings.map { it.id }
                    repository.findUsersById(userId.toSet())
                }.onFailure { logE(throwable = it) { "load(refresh):" } }
                MediatorResult.Success(true)
            }

            LoadType.PREPEND, LoadType.APPEND -> {
                val items: List<TwitchUser.Id> = state.pages.map { it.data }.flatten()
                    .filter { it.channel.iconUrl.isEmpty() }
                    .map { it.channel.id.mapTo() }
                repository.findUsersById(items.toSet())
                    .onFailure { logE(throwable = it) { "load($loadType):" } }
                MediatorResult.Success(true)
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class TwitchSubscriptionPagerFactory @Inject constructor(
    remoteMediator: TwitchRemoteMediator,
    @LivePlatformQualifier(Twitch::class) function: PagingSourceFunction<LiveSubscription>,
) : PagerFactory<LiveSubscription> by PagerFactory.newInstance(remoteMediator, function)
