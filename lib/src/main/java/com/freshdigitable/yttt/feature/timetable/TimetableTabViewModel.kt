package com.freshdigitable.yttt.feature.timetable

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.compose.SnackbarMessage
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.compose.onFailureWithSnackbarMessage
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.video.FindLiveVideoUseCase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration

@HiltViewModel(assistedFactory = TimetableTabViewModel.Factory::class)
internal class TimetableTabViewModel @AssistedInject constructor(
    private val settingRepository: SettingRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    contextMenuDelegateFactory: TimetableContextMenuDelegate.Factory,
    private val dateTimeProvider: DateTimeProvider,
    timetablePageDelegate: TimetablePageDelegate,
    @Assisted private val sender: SnackbarMessageBus.Sender,
) : ViewModel(),
    TimetablePageDelegate by timetablePageDelegate {
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
    private val contextMenuDelegate = contextMenuDelegateFactory.create(sender)
    fun loadList() {
        viewModelScope.launch {
            if (_isLoading.value == false) {
                _isLoading.postValue(true)
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

    val menuItems: StateFlow<List<TimetableMenuItem>> = contextMenuDelegate.menuItems
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onMenuClicked(id: LiveVideo.Id) {
        viewModelScope.launch {
            contextMenuDelegate.setupMenu(id)
        }
    }

    fun onMenuClosed() {
        contextMenuDelegate.tearDownMenu()
    }

    fun onMenuItemClicked(item: TimetableMenuItem) {
        viewModelScope.launch {
            contextMenuDelegate.consumeMenuItem(item)
        }
    }

    val tabData: StateFlow<List<TimetableTabData>> =
        tabs.stateIn(viewModelScope, SharingStarted.Lazily, TimetableTabData.initialValues())

    companion object {
        @Suppress("unused")
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}

class TimetableContextMenuDelegate @AssistedInject constructor(
    private val findLiveVideoMap: IdBaseClassMap<FindLiveVideoUseCase>,
    private val menuSelectorMap: IdBaseClassMap<TimetableContextMenuSelector>,
    @Assisted private val sender: SnackbarMessageBus.Sender,
) {
    @AssistedFactory
    interface Factory {
        fun create(sender: SnackbarMessageBus.Sender): TimetableContextMenuDelegate
    }

    private val _selectedLiveVideo = MutableStateFlow<LiveVideo<*>?>(null)
    private val selected: LiveVideo<*> get() = checkNotNull(_selectedLiveVideo.value)
    private val menuSelector get() = checkNotNull(menuSelectorMap[selected.id.type.java])
    val menuItems: Flow<List<TimetableMenuItem>> = _selectedLiveVideo.map {
        if (it == null) {
            emptyList()
        } else {
            menuSelector.findMenuItems(it)
        }
    }

    suspend fun setupMenu(id: LiveVideo.Id) {
        checkNotNull(findLiveVideoMap[id.type.java]).invoke(id)
            .onFailureWithSnackbarMessage(sender)
            .onSuccess { _selectedLiveVideo.value = it }
    }

    suspend fun consumeMenuItem(item: TimetableMenuItem) {
        val video = checkNotNull(_selectedLiveVideo.value)
        menuSelector.consumeMenuItem(video, item)
    }

    fun tearDownMenu() {
        _selectedLiveVideo.value = null
    }
}
