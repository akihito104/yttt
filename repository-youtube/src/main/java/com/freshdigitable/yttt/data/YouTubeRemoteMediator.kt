package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.PagingSourceFunction
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.logD
import java.time.Duration
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeRemoteMediator @Inject constructor(
    private val repository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, LiveSubscription>() {
    override suspend fun initialize(): InitializeAction {
        val subscriptionFetchedAt = repository.subscriptionsFetchedAt
        if (subscriptionFetchedAt + Duration.ofMinutes(30) <= dateTimeProvider.now()) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, LiveSubscription>,
    ): MediatorResult {
        logD { "load: $loadType, $state" }
        if (!accountRepository.hasAccount()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }
        return when (loadType) {
            LoadType.REFRESH -> {
                val res = repository.fetchAllSubscribe(YouTubeDataSource.MAX_PAGE_SIZE)
                    .onSuccess { s ->
                        (s as? YouTubeSubscriptions.Updated)?.let {
                            repository.addSubscribes(it)
                            repository.removeSubscribes(it.deleted)
                        }
                    }
                if (res.isSuccess) MediatorResult.Success(true)
                else MediatorResult.Error(res.exceptionOrNull()!!)
            }

            LoadType.PREPEND, LoadType.APPEND -> MediatorResult.Success(true)
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeSubscriptionPagerFactory @Inject constructor(
    remoteMediator: YouTubeRemoteMediator,
    @LivePlatformQualifier(YouTube::class) function: PagingSourceFunction<LiveSubscription>,
) : PagerFactory<LiveSubscription> by PagerFactory.newInstance(remoteMediator, function)
