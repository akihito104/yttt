package com.freshdigitable.yttt

import androidx.lifecycle.ViewModel
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.toLiveSubscription
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.multibindings.IntoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val useCases: IdBaseClassMap<FetchSubscriptionListSourceUseCase>,
) : ViewModel() {
    fun getSubscriptionSource(platform: LivePlatform): Flow<List<LiveSubscription>> {
        val id = when (platform) {
            LivePlatform.YOUTUBE -> YouTubeId::class
            LivePlatform.TWITCH -> TwitchId::class
        }
        val fetchSubscriptionListSource = checkNotNull(useCases[id.java])
        return fetchSubscriptionListSource()
    }
}

interface FetchSubscriptionListSourceUseCase {
    operator fun invoke(): Flow<List<LiveSubscription>>
}

class FetchSubscriptionListSourceFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> {
        return repository.subscriptions
            .map { i -> i.map { it.toLiveSubscription() } }
    }
}

class FetchSubscriptionListSourceFromTwitchUseCase @Inject constructor(
    private val repository: TwitchLiveRepository,
) : FetchSubscriptionListSourceUseCase {
    override fun invoke(): Flow<List<LiveSubscription>> = flow {
        val me = repository.fetchMe()
        if (me == null) {
            emit(emptyList())
            return@flow
        }
        val broadcasters = repository.fetchAllFollowings(me.id)
        val userIds = broadcasters.map { it.id }
        val users = repository.findUsersById(userIds).associateBy { it.id }
        val followings = broadcasters.mapIndexed { i, b ->
            val u = checkNotNull(users[b.id])
            b.toLiveSubscription(i, u)
        }
        emit(followings)
    }
}

@Module
@InstallIn(ViewModelComponent::class)
interface FetchSubscriptionListUseCaseModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeId::class)
    fun bindFetchSubscriptionListFromYouTubeUseCase(
        useCase: FetchSubscriptionListSourceFromYouTubeUseCase,
    ): FetchSubscriptionListSourceUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchId::class)
    fun bindFetchSubscriptionListFromTwitchUseCase(
        useCase: FetchSubscriptionListSourceFromTwitchUseCase,
    ): FetchSubscriptionListSourceUseCase
}
