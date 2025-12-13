package com.freshdigitable.yttt.feature.timetable

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.compose.SnackbarMessage
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.logE
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel(assistedFactory = TimetableTabViewModel.Factory::class)
internal class TimetableTabViewModel @AssistedInject constructor(
    private val settingRepository: SettingRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val contextMenuDelegate: TimetableContextMenuDelegate,
    private val dateTimeProvider: DateTimeProvider,
    private val timetablePageDelegate: TimetablePageDelegate,
    @Assisted private val sender: SnackbarMessageBus.Sender,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(sender: SnackbarMessageBus.Sender): TimetableTabViewModel
    }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    val canUpdate: Boolean
        get() {
            val lastUpdateDatetime = settingRepository.lastUpdateDatetime ?: return true
            return (lastUpdateDatetime + Duration.ofMinutes(30)) <= dateTimeProvider.now()
        }
    private val current: MutableStateFlow<Instant> = MutableStateFlow(dateTimeProvider.now())
    fun loadList() {
        viewModelScope.launch {
            if (_isLoading.value == false) {
                _isLoading.postValue(true)
                current.value = dateTimeProvider.now()
                AppPerformance.trace("loadList") {
                    val tasks = fetchStreamTasks.map { async { it() } }.awaitAll()
                    if (tasks.isNotEmpty() && tasks.all { it.isSuccess }) {
                        settingRepository.lastUpdateDatetime = dateTimeProvider.now()
                    } else {
                        val failed = checkNotNull(tasks.first { it.isFailure }.exceptionOrNull())
                        sender.send(SnackbarMessage.fromThrowable(failed))
                        tasks.filter { it.isFailure }.forEach {
                            logE(throwable = it.exceptionOrNull()) { "error: ${it.exceptionOrNull()?.message}" }
                        }
                    }
                }
                _isLoading.postValue(false)
            }
        }
    }

    val menuSelector: StateFlow<ContextMenuSelector<TimetableMenuItem>> = contextMenuDelegate.menuSelector
        .stateIn(viewModelScope, SharingStarted.Lazily, TimetableContextMenuSelector.EMPTY)

    fun onMenuClick(id: LiveVideo.Id) {
        contextMenuDelegate.setupMenu(id)
    }

    fun onMenuClose() {
        contextMenuDelegate.tearDownMenu()
    }

    val pagers = TimetablePage.entries.associateWith { p ->
        current.flatMapLatest { timetablePageDelegate.getTimetableItemPager(p)(it) }
            .cachedIn(viewModelScope)
    }
}

class TimetableContextMenuDelegate @Inject constructor(
    private val menuSelectorMap: IdBaseClassMap<TimetableContextMenuSelector>,
) {
    private val _selectedLiveVideoId = MutableStateFlow<LiveVideo.Id?>(null)
    private val selected: LiveVideo.Id get() = checkNotNull(_selectedLiveVideoId.value)
    private val menuSelectorFactory get() = checkNotNull(menuSelectorMap[selected.type.java])
    val menuSelector: Flow<ContextMenuSelector<TimetableMenuItem>> = _selectedLiveVideoId.map {
        if (it == null) {
            TimetableContextMenuSelector.EMPTY
        } else {
            menuSelectorFactory.setupMenuItems(it)
        }
    }

    fun setupMenu(id: LiveVideo.Id) {
        _selectedLiveVideoId.value = id
    }

    fun tearDownMenu() {
        _selectedLiveVideoId.value = null
    }
}
