package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.local.db.YouTubeLiveSubscription
import com.freshdigitable.yttt.data.source.local.db.YouTubePagingSource
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class YouTubeRemoteMediator @Inject constructor(
    private val pagingSource: YouTubePagingSource,
) : RemoteMediator<Int, YouTubeLiveSubscription>() {
    val page: Flow<PagingData<LiveSubscription>> = Pager(
        config = PagingConfig(pageSize = 50),
        remoteMediator = this,
    ) {
        pagingSource.getYouTubeLiveSubscriptionPageSource()
    }.flow.map { i -> i.map { it } }

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, YouTubeLiveSubscription>
    ): MediatorResult {
        logD { "load: $loadType, $state" }
        return MediatorResult.Success(endOfPaginationReached = true)
    }
}
