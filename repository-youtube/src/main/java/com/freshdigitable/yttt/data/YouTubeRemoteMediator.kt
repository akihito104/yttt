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
import com.freshdigitable.yttt.data.source.local.db.YouTubeLiveSubscription
import com.freshdigitable.yttt.data.source.local.db.YouTubePagingSource
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class YouTubeRemoteMediator @Inject constructor(
    private val pagingSource: YouTubePagingSource,
    private val repository: YouTubeRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, YouTubeLiveSubscription>() {
    val page: Flow<PagingData<LiveSubscription>> = Pager(
        config = PagingConfig(pageSize = 50),
        remoteMediator = this,
    ) {
        pagingSource.getYouTubeLiveSubscriptionPageSource()
    }.flow.map { i -> i.map { it } }

    override suspend fun initialize(): InitializeAction {
        val subscriptionFetchedAt = repository.subscriptionFetchedAt ?: Instant.EPOCH
        if (subscriptionFetchedAt + Duration.ofMinutes(30) <= dateTimeProvider.now()) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, YouTubeLiveSubscription>,
    ): MediatorResult {
        logD { "load: $loadType, $state" }
        return when (loadType) {
            LoadType.REFRESH -> {
                repository.fetchAllSubscribe(50)
                MediatorResult.Success(false)
            }

            LoadType.PREPEND -> MediatorResult.Success(true)
            LoadType.APPEND -> MediatorResult.Success(state.lastItemOrNull() == null)
        }
    }
}
