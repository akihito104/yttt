package com.freshdigitable.yttt

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TimetableMenuItem
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val liveRepository: YouTubeRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val findLiveVideoTable: IdBaseClassMap<FindLiveVideoUseCase>,
    timetablePageFacade: TimetablePageFacade,
) : ViewModel(), TimetablePageFacade by timetablePageFacade {
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
                TimetableMenuItem.LAUNCH_LIVE,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onMenuClicked(id: LiveVideo.Id) {
        val findLiveVideo = checkNotNull(findLiveVideoTable[id.type.java])
        viewModelScope.launch {
            val v = findLiveVideo(id)
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
                if (id.type == YouTubeVideo.Id::class) {
                    checkAsFreeChat(id)
                }
            }

            TimetableMenuItem.REMOVE_FREE_CHAT -> {
                if (id.type == YouTubeVideo.Id::class) {
                    uncheckAsFreeChat(id)
                }
            }

            TimetableMenuItem.LAUNCH_LIVE -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                appLauncher(intent)
            }
        }
    }

    private fun checkAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.addFreeChatItems(listOf(id.mapTo()))
        }
    }

    private fun uncheckAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.removeFreeChatItems(listOf(id.mapTo()))
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}
