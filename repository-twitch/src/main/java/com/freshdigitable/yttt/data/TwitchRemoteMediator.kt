package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.local.db.TwitchLiveSubscription
import com.freshdigitable.yttt.data.source.local.db.TwitchPagingSource
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class TwitchRemoteMediator @Inject constructor(
    private val pagingSource: TwitchPagingSource,
    private val repository: TwitchLiveRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, TwitchLiveSubscription>() {
    val pager: Flow<PagingData<LiveSubscription>> = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = this,
    ) {
        pagingSource.getTwitchLiveSubscriptionPagingSource()
    }.flow.map { p -> p.map { it } }

    override suspend fun initialize(): InitializeAction {
        if (pagingSource.isUpdatable(dateTimeProvider.now())) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TwitchLiveSubscription>,
    ): MediatorResult {
        logD { "load: $loadType, $state" }
        return when (loadType) {
            LoadType.REFRESH -> {
                val me = repository.fetchMe()
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
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
