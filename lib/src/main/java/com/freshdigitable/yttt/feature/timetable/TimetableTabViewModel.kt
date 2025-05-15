package com.freshdigitable.yttt.feature.timetable

import androidx.compose.material3.SnackbarVisuals
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.compose.HorizontalPagerTabViewModel
import com.freshdigitable.yttt.compose.SnackbarMessage
import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.compose.onFailureWithSnackbarMessage
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.video.FindLiveVideoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
internal class TimetableTabViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    contextMenuDelegateFactory: TimetableContextMenuDelegate.Factory,
    private val dateTimeProvider: DateTimeProvider,
    timetablePageDelegate: TimetablePageDelegate,
) : ViewModel(), TimetablePageDelegate by timetablePageDelegate,
    HorizontalPagerTabViewModel<TimetableTabData> {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    val canUpdate: Boolean
        get() {
            val lastUpdateDatetime = settingRepository.lastUpdateDatetime ?: return true
            return (lastUpdateDatetime + Duration.ofMinutes(30)) <= dateTimeProvider.now()
        }
    private val _snackbarChannel = Channel<SnackbarVisuals>()
    val snackbarChannel: ReceiveChannel<SnackbarVisuals> get() = _snackbarChannel
    private val contextMenuDelegate = contextMenuDelegateFactory.create(_snackbarChannel)
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
                        _snackbarChannel.send(SnackbarMessage.fromThrowable(failed))
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

    override fun onCleared() {
        super.onCleared()
        _snackbarChannel.close()
    }

    override val tabData: Flow<List<TimetableTabData>> get() = tabs
    override val initialTab: List<TimetableTabData> = TimetableTabData.initialValues()

    companion object {
        @Suppress("unused")
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}

class TimetableContextMenuDelegate @AssistedInject constructor(
    private val findLiveVideoMap: IdBaseClassMap<FindLiveVideoUseCase>,
    private val menuSelectorMap: IdBaseClassMap<TimetableContextMenuSelector>,
    @Assisted private val errorMessageChannel: SendChannel<SnackbarVisuals>,
) {
    @AssistedFactory
    interface Factory {
        fun create(errorMessageChannel: SendChannel<SnackbarVisuals>): TimetableContextMenuDelegate
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
            .onFailureWithSnackbarMessage(errorMessageChannel)
            .onSuccess { _selectedLiveVideo.value = it }
    }

    suspend fun consumeMenuItem(item: TimetableMenuItem) {
        val video = checkNotNull(_selectedLiveVideo.value)
        menuSelector.consumeMenuItem(video, item)
    }

    fun tearDownMenu() {
        _selectedLiveVideo.value = null
    }

    suspend fun findMenuItems(videoId: LiveVideo.Id): List<TimetableMenuItem> {
        val useCase = checkNotNull(findLiveVideoMap[videoId.type.java])
        return useCase(videoId)
            .onFailureWithSnackbarMessage(errorMessageChannel)
            .map { v -> v?.let { menuSelector.findMenuItems(it) } ?: emptyList() }
            .getOrDefault(emptyList())
    }
}
