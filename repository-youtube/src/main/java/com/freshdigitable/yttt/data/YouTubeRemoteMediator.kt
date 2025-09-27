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
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
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
    companion object {
        private val MAX_AGE_SUBSCRIPTION = Duration.ofHours(2)
        private val YouTubeDataSource.Extended.subscriptionsOrderedCacheControl: CacheControl
            get() = CacheControl.create(
                subscriptionsRelevanceOrderedFetchedAt,
                MAX_AGE_SUBSCRIPTION,
            )
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
            LoadType.REFRESH -> fetchPagedSubscriptions()
            LoadType.PREPEND, LoadType.APPEND -> Unit
        }
        return MediatorResult.Success(true)
    }

    private suspend fun fetchPagedSubscriptions(): MediatorResult {
        var token: String? = null
        val items = mutableListOf<YouTubeSubscription>()
        do {
            val query = YouTubeSubscriptionQuery.forRelevance(items.size, token)
            repository.fetchPagedSubscription(query).onFailure {
                return MediatorResult.Error(it)
            }.onSuccess { r ->
                items.addAll(r.item)
                token = r.nextPageToken
            }
        } while (token != null)
        repository.syncSubscriptionList(items.map { it.id }.toSet(), emptyList())
        repository.cleanUp()
        return MediatorResult.Success(true)
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class YouTubeSubscriptionPagerFactory @Inject constructor(
    remoteMediator: YouTubeRemoteMediator,
    @LivePlatformQualifier(YouTube::class) function: PagingSourceFunction<LiveSubscription>,
) : PagerFactory<LiveSubscription> by PagerFactory.newInstance(remoteMediator, function)
