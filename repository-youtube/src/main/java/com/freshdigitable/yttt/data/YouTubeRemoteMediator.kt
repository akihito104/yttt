package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.RemoteMediatorFactory
import com.freshdigitable.yttt.logD
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant

@AssistedFactory
internal interface YouTubeRemoteMediatorFactory : RemoteMediatorFactory<LiveSubscription> {
    @ExperimentalPagingApi
    override fun create(): YouTubeRemoteMediator
}

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeRemoteMediator @AssistedInject constructor(
    private val repository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : RemoteMediator<Int, LiveSubscription>() {
    override suspend fun initialize(): InitializeAction {
        val subscriptionFetchedAt = repository.subscriptionFetchedAt ?: Instant.EPOCH
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
                repository.fetchAllSubscribe(50)
                MediatorResult.Success(true)
            }

            LoadType.PREPEND, LoadType.APPEND -> MediatorResult.Success(true)
        }
    }
}
