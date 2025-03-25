package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.RemoteMediatorFactory
import com.freshdigitable.yttt.data.source.local.db.TwitchPagingSource
import com.freshdigitable.yttt.logD
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@AssistedFactory
internal interface TwitchSubscriptionRemoteMediator :
    RemoteMediatorFactory<LiveSubscription> {
    @ExperimentalPagingApi
    override fun create(): TwitchRemoteMediator
}

@OptIn(ExperimentalPagingApi::class)
internal class TwitchRemoteMediator @AssistedInject constructor(
    private val pagingSource: TwitchPagingSource,
    private val repository: TwitchLiveRepository,
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
            ?: return MediatorResult.Success(endOfPaginationReached = true)
        return when (loadType) {
            LoadType.REFRESH -> {
                val followings = repository.fetchAllFollowings(me.id)
                val userId = followings.followings.map { it.id }
                repository.findUsersById(userId.toSet())
                MediatorResult.Success(true)
            }

            LoadType.PREPEND, LoadType.APPEND -> {
                val items: List<TwitchUser.Id> = state.pages.map { it.data }.flatten()
                    .filter { it.channel.iconUrl.isEmpty() }
                    .map { it.channel.id.mapTo() }
                repository.findUsersById(items.toSet())
                MediatorResult.Success(true)
            }
        }
    }
}
