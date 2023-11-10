package com.freshdigitable.yttt

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TimetableMenuItem
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
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
    private val liveRepository: YouTubeLiveRepository,
    private val fetchStreamTasks: Set<@JvmSuppressWildcards FetchStreamUseCase>,
    private val findLiveVideoFromTwitch: FindLiveVideoFromTwitchUseCase,
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
        private val TAG = TimetableTabViewModel::class.java.simpleName
    }
}
