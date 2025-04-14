package com.freshdigitable.yttt.feature.timetable

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.compose.HorizontalPagerTabViewModel
import com.freshdigitable.yttt.compose.TimetableTabData
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.video.FindLiveVideoUseCase
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
import javax.inject.Inject

@HiltViewModel
internal class TimetableTabViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val contextMenuDelegate: TimetableContextMenuDelegate,
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

    fun loadList() {
        viewModelScope.launch {
            if (_isLoading.value == false) {
                _isLoading.postValue(true)
                val trace = AppPerformance.newTrace("loadList")
                trace.start()
                fetchStreamTasks.map { async { it() } }.awaitAll()
                trace.stop()
                settingRepository.lastUpdateDatetime = dateTimeProvider.now()
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

    override val tabData: Flow<List<TimetableTabData>> get() = tabs
    override val initialTab: List<TimetableTabData> = TimetableTabData.initialValues()

    companion object {
        @Suppress("unused")
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}

class TimetableContextMenuDelegate @Inject constructor(
    private val findLiveVideoMap: IdBaseClassMap<FindLiveVideoUseCase>,
    private val menuSelectorMap: IdBaseClassMap<TimetableContextMenuSelector>,
) {
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
        val video = checkNotNull(findLiveVideoMap[id.type.java]).invoke(id)
        _selectedLiveVideo.value = video
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
        val video = useCase(videoId) ?: return emptyList()
        return menuSelector.findMenuItems(video)
    }
}
