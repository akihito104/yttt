package com.freshdigitable.yttt

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.compose.TimetableMenuItem
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.model.toTwitchVideoList
import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.multibindings.IntoSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Qualifier

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val findLiveVideoFromTwitch: FindLiveVideoFromTwitchUseCase,
    timetableTabViewModel: TimetableTabViewModelImpl,
) : ViewModel(), TimetableTabViewModel by timetableTabViewModel {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    val canUpdate: Boolean
        get() {
            val lastUpdateDatetime = liveRepository.lastUpdateDatetime ?: return true
            return (lastUpdateDatetime + Duration.ofMinutes(30)) <= Instant.now()
        }

    fun loadList() {
        viewModelScope.launch {
            if (_isLoading.value == false) {
                _isLoading.postValue(true)
                fetchStreamTasks.map { async { it() } }.awaitAll()
                _isLoading.postValue(false)
            }
        }
    }

    private val _selectedItem: MutableStateFlow<LiveVideo?> = MutableStateFlow(null)
    val menuItems: StateFlow<List<TimetableMenuItem>> = _selectedItem.map {
        if (it == null) emptyList()
        else {
            listOfNotNull(
                if (it.isFreeChat == true) TimetableMenuItem.REMOVE_FREE_CHAT else TimetableMenuItem.ADD_FREE_CHAT,
                if (it.id.platform == LivePlatform.TWITCH && !it.isNowOnAir()) null else TimetableMenuItem.LAUNCH_LIVE,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onMenuClicked(id: LiveVideo.Id) {
        viewModelScope.launch {
            val v = when (id.platform) {
                LivePlatform.YOUTUBE -> liveRepository.fetchVideoDetail(id)
                LivePlatform.TWITCH -> findLiveVideoFromTwitch(id)
            }
            _selectedItem.value = v
        }
    }

    fun onMenuClosed() {
        _selectedItem.value = null
    }

    fun onMenuItemClicked(item: TimetableMenuItem, appLauncher: (Intent) -> Unit) {
        val video = checkNotNull(_selectedItem.value)
        val id = video.id
        when (item) {
            TimetableMenuItem.ADD_FREE_CHAT -> {
                if (id.platform == LivePlatform.YOUTUBE) {
                    checkAsFreeChat(id)
                }
            }

            TimetableMenuItem.REMOVE_FREE_CHAT -> {
                if (id.platform == LivePlatform.YOUTUBE) {
                    uncheckAsFreeChat(id)
                }
            }

            TimetableMenuItem.LAUNCH_LIVE -> {
                val url = when (id.platform) {
                    LivePlatform.YOUTUBE -> "https://youtube.com/watch?v=${id.value}"
                    LivePlatform.TWITCH -> "https://twitch.tv/${(video.channel as LiveChannelDetail).customUrl}"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                appLauncher(intent)
            }
        }
    }

    private fun checkAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.addFreeChatItems(listOf(id))
        }
    }

    private fun uncheckAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.removeFreeChatItems(listOf(id))
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = MainViewModel::class.java.simpleName
    }
}

interface FetchStreamUseCase {
    suspend operator fun invoke()
}

class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val accountRepository: AccountRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (!accountRepository.hasAccount()) {
            return
        }
        updateStreams()
        fetchNewStreams()
        liveRepository.lastUpdateDatetime = Instant.now()
        liveRepository.cleanUp()
        updateAsFreeChat()
        Log.d(TAG, "fetchLiveStreams: end")
    }

    private suspend fun updateStreams() {
        val first = liveRepository.findAllUnfinishedVideos()
            .filter { it.isNowOnAir() || it.isUpcoming() }
            .map { it.id }.distinct()
        val currentVideo = liveRepository.fetchVideoList(first).map { it.id }.toSet()
        Log.d(TAG, "fetchLiveStreams: currentVideo> ${currentVideo.size}")
        val removed = first.subtract(currentVideo)
        Log.d(TAG, "fetchLiveStreams: removed> ${removed.size}")
        liveRepository.updateVideosInvisible(removed)
    }

    private suspend fun fetchNewStreams() {
        val channelIds = liveRepository.fetchAllSubscribe(maxResult = 50).map { it.channel.id }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val channelDetails = liveRepository.fetchChannelList(channelIds)
        val task = coroutineScope {
            channelDetails.map { channelDetail -> async { fetchVideoTask(channelDetail) } }
        }
        val ids = task.awaitAll().flatten()
        liveRepository.fetchVideoList(ids)
    }

    private suspend fun fetchVideoTask(channelDetail: LiveChannelDetail): List<LiveVideo.Id> {
        val id = channelDetail.uploadedPlayList ?: return emptyList()
        try {
            val ids = liveRepository.fetchVideoIdListByPlaylistId(id)
            Log.d(TAG, "fetchLiveStreams: playlistId> $id,count>${ids.size}")
            return ids
        } catch (e: Exception) {
            if ((e as? GoogleJsonResponseException)?.statusCode == 404) {
                Log.d(TAG, "fetchLiveStreams(reload ${channelDetail.customUrl}) did not update.")
            } else {
                Log.e(TAG, "fetchLiveStreams: channel>$channelDetail", e)
            }
        }
        return emptyList()
    }

    private suspend fun updateAsFreeChat() {
        val unchecked = liveRepository.findAllUnfinishedVideos()
        val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )
        val freeChat = unchecked.filter { it.isFreeChat == null }
            .filter { v -> regex.any { v.title.contains(it) } }
            .map { it.id }
        liveRepository.addFreeChatItems(freeChat)
        val unfinished = unchecked.filter { it.isFreeChat == null }.map { it.id } - freeChat.toSet()
        liveRepository.removeFreeChatItems(unfinished)
    }

    companion object {
        @Suppress("unused")
        private val TAG = FetchYouTubeStreamUseCase::class.simpleName
    }
}

class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (accountRepository.getTwitchToken() == null) {
            return
        }
        val me = twitchRepository.fetchMe() ?: return
        val streams = twitchRepository.fetchFollowedStreams()
        val following = twitchRepository.fetchAllFollowings(me.id)
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll()
        val users = streams.map { it.user.id } + schedules.flatten().map { it.broadcaster.id }
        twitchRepository.findUsersById(users)
    }
}

@InstallIn(ViewModelComponent::class)
@Module
interface FetchStreamUseCaseModules {
    @Binds
    @IntoSet
    fun bindYouTubeStreamUseCase(useCase: FetchYouTubeStreamUseCase): FetchStreamUseCase

    @Binds
    @IntoSet
    fun bindTwitchStreamUseCase(useCase: FetchTwitchStreamUseCase): FetchStreamUseCase
}

interface TimetableTabViewModel {
    fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>>
    fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>>
    val tabs: Flow<List<TabData>>
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Qualifier
annotation class TimetableTabQualifier(val page: TimetablePage)

@InstallIn(ViewModelComponent::class)
@Module
object TimetableTabModules {
    @Provides
    @TimetableTabQualifier(TimetablePage.OnAir)
    @IntoSet
    fun provideTwitchOnAirItemList(repository: TwitchLiveRepository): Flow<List<LiveVideo>> =
        repository.onAir.map {
            it.map { s ->
                val user = s.user as? TwitchUserDetail
                    ?: repository.findUsersById(listOf(s.user.id)).first()
                s.toLiveVideo(user)
            }
        }

    @Provides
    @TimetableTabQualifier(TimetablePage.OnAir)
    @IntoSet
    fun provideYouTubeOnAirItemList(repository: YouTubeLiveRepository): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isNowOnAir() } }

    @Provides
    @TimetableTabQualifier(TimetablePage.Upcoming)
    @IntoSet
    fun provideYouTubeUpcomingItemList(repository: YouTubeLiveRepository): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isUpcoming() } }

    @Provides
    @TimetableTabQualifier(TimetablePage.Upcoming)
    @IntoSet
    fun provideTwitchUpcomingItemList(repository: TwitchLiveRepository): Flow<List<LiveVideo>> =
        repository.upcoming.map { u ->
            val week = Instant.now().plus(Duration.ofDays(7L))
            u.map { s -> s.toTwitchVideoList() }.flatten()
                .filter { it.schedule.startTime.isBefore(week) }
                .map { s ->
                    val user = s.user as? TwitchUserDetail
                        ?: repository.findUsersById(listOf(s.user.id)).first()
                    s.toLiveVideo(user)
                }
        }

    @Provides
    @TimetableTabQualifier(TimetablePage.FreeChat)
    @IntoSet
    fun provideYouTubeFreeChatItemList(repository: YouTubeLiveRepository): Flow<List<LiveVideo>> =
        repository.videos.map { v -> v.filter { it.isFreeChat == true } }

    @Provides
    fun provideItemList(
        @TimetableTabQualifier(TimetablePage.OnAir) onAirItems: Set<@JvmSuppressWildcards Flow<List<LiveVideo>>>,
        @TimetableTabQualifier(TimetablePage.Upcoming) upcomingItems: Set<@JvmSuppressWildcards Flow<List<LiveVideo>>>,
        @TimetableTabQualifier(TimetablePage.FreeChat) freeChatItems: Set<@JvmSuppressWildcards Flow<List<LiveVideo>>>,
    ): Map<TimetablePage, Flow<List<LiveVideo>>> {
        return mapOf(
            TimetablePage.OnAir to combine(onAirItems) { i -> i.flatMap { it } },
            TimetablePage.Upcoming to combine(upcomingItems) { i -> i.flatMap { it } },
            TimetablePage.FreeChat to combine(freeChatItems) { i -> i.flatMap { it } },
        )
    }
}

class TimetableTabViewModelImpl @Inject constructor(
    items: Map<TimetablePage, @JvmSuppressWildcards Flow<List<LiveVideo>>>,
    prefs: AndroidPreferencesDataStore,
) : TimetableTabViewModel {
    private val mapperTable =
        mapOf<TimetablePage, (List<LiveVideo>) -> List<LiveVideo>>(
            TimetablePage.OnAir to { i ->
                i.filter { it.isNowOnAir() }
                    .sortedByDescending { it.actualStartDateTime }
            },
            TimetablePage.Upcoming to { i ->
                i.filter { it.isUpcoming() && it.isFreeChat != true }
                    .sortedBy { it.scheduledStartDateTime }
            },
            TimetablePage.FreeChat to { i ->
                i.sortedBy { it.channel.id.value }
            },
        )
    private val sourceTable: Map<TimetablePage, Flow<List<LiveVideo>>> = items.entries
        .associate { i ->
            i.key to combine(i.value) { v -> v.flatMap { it } }
                .map { checkNotNull(mapperTable[i.key]).invoke(it) }
        }

    override fun getSimpleItemList(page: TimetablePage): Flow<List<LiveVideo>> =
        checkNotNull(sourceTable[page])

    private val extraHourOfDay = prefs.changeDateTime.map {
        Duration.ofHours(((it ?: 24) - 24).toLong())
    }
    private val upcomingItems: Flow<Map<String, List<LiveVideo>>> =
        combine(sourceTable[TimetablePage.Upcoming]!!, extraHourOfDay) { v, t ->
            v.groupBy {
                (checkNotNull(it.scheduledStartDateTime) - t)
                    .toLocalDateTime()
                    .truncatedTo(ChronoUnit.DAYS)
                    .format(dateWeekdayFormatter)
            }
        }
    private val groupedItemLists = mapOf(
        TimetablePage.Upcoming to upcomingItems,
    )

    override fun getGroupedItemList(page: TimetablePage): Flow<Map<String, List<LiveVideo>>> =
        checkNotNull(groupedItemLists[page])

    override val tabs: Flow<List<TabData>> = combine(
        sourceTable.entries.map { (k, v) ->
            v.map { it.size }.distinctUntilChanged().map { TabData(k, it) }
        }
    ) {
        it.toList().sorted()
    }
}
