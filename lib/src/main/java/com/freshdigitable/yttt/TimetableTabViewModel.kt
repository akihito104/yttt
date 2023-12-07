package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TimetableMenuItem
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.di.IdBaseClassMap
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
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TimetableTabViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val contextMenuDelegate: TimetableContextMenuDelegate,
    timetablePageFacade: TimetablePageFacade,
) : ViewModel(), TimetablePageFacade by timetablePageFacade {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    val canUpdate: Boolean
        get() {
            val lastUpdateDatetime = settingRepository.lastUpdateDatetime ?: return true
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

    companion object {
        @Suppress("unused")
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}

class TimetableContextMenuDelegate @Inject constructor(
    private val findLiveVideoMap: IdBaseClassMap<FindLiveVideoUseCase>,
    private val menuSelectorMap: IdBaseClassMap<MenuSelector>,
) {
    private val _selectedLiveVideo = MutableStateFlow<LiveVideo?>(null)
    private val selected: LiveVideo get() = checkNotNull(_selectedLiveVideo.value)
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

    interface MenuSelector {
        fun findMenuItems(video: LiveVideo): List<TimetableMenuItem>
        suspend fun consumeMenuItem(video: LiveVideo, item: TimetableMenuItem)
    }
}

class TimetableContextMenuDelegateForYouTube @Inject constructor(
    private val repository: YouTubeRepository,
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuDelegate.MenuSelector {
    override fun findMenuItems(video: LiveVideo): List<TimetableMenuItem> {
        return listOfNotNull(
            if (video.isFreeChat == true) TimetableMenuItem.REMOVE_FREE_CHAT else TimetableMenuItem.ADD_FREE_CHAT,
            TimetableMenuItem.LAUNCH_LIVE,
        )
    }

    override suspend fun consumeMenuItem(video: LiveVideo, item: TimetableMenuItem) {
        val id = video.id
        when (item) {
            TimetableMenuItem.ADD_FREE_CHAT -> {
                repository.addFreeChatItems(listOf(id.mapTo()))
            }

            TimetableMenuItem.REMOVE_FREE_CHAT -> {
                repository.removeFreeChatItems(listOf(id.mapTo()))
            }

            TimetableMenuItem.LAUNCH_LIVE -> {
                launchApp(video.url)
            }
        }
    }
}

class TimetableContextMenuDelegateForTwitch @Inject constructor(
    private val launchApp: LaunchAppWithUrlUseCase,
) : TimetableContextMenuDelegate.MenuSelector {
    override fun findMenuItems(video: LiveVideo): List<TimetableMenuItem> {
        return listOf(TimetableMenuItem.LAUNCH_LIVE)
    }

    override suspend fun consumeMenuItem(video: LiveVideo, item: TimetableMenuItem) {
        if (item == TimetableMenuItem.LAUNCH_LIVE) {
            launchApp(video.url)
        }
    }
}
