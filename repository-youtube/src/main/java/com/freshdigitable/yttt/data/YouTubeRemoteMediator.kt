package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.PagingSourceFunction
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.YouTubeLiveDataSource
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.last
import java.time.Duration
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeRemoteMediator @Inject constructor(
    private val repository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, LiveSubscription>() {
    companion object {
        private val MAX_AGE_SUBSCRIPTION = Duration.ofHours(2)
        private val YouTubeLiveDataSource.subscriptionsOrderedCacheControl: CacheControl
            get() = CacheControl.create(subscriptionsOrderedFetchedAt, MAX_AGE_SUBSCRIPTION)
    }

    override suspend fun initialize(): InitializeAction {
        if (repository.subscriptionsOrderedCacheControl.isUpdatable(dateTimeProvider.now())) {
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
        when (loadType) {
            LoadType.REFRESH -> {
                repository.fetchSubscriptions(YouTubeDataSource.MAX_PAGE_SIZE).last()
                    .onFailure { return MediatorResult.Error(it) }
                    .onSuccess { s ->
                        (s as? YouTubeSubscriptions.Updated)?.let {
                            repository.addSubscribes(it)
                            repository.subscriptionsOrderedFetchedAt = it.lastUpdatedAt
                            repository.removeSubscribesByRemainingIds(it.ids)
                        }
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }
            }

            LoadType.PREPEND, LoadType.APPEND -> Unit
        }
        return MediatorResult.Success(true)
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeSubscriptionPagerFactory @Inject constructor(
    remoteMediator: YouTubeRemoteMediator,
    @LivePlatformQualifier(YouTube::class) function: PagingSourceFunction<LiveSubscription>,
) : PagerFactory<LiveSubscription> by PagerFactory.newInstance(remoteMediator, function)
