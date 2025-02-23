package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import kotlinx.coroutines.flow.Flow

interface ChannelDetailDelegate {
    val tabs: List<ChannelPage>
    val channelDetailBody: Flow<LiveChannelDetailBody?>
    val annotatedDetail: Flow<AnnotatableString>
    val uploadedVideo: Flow<List<LiveVideoThumbnail>>
    val channelSection: Flow<List<ChannelDetailChannelSection>>
    val activities: Flow<List<LiveVideo<*>>>
    suspend fun clearForDetail() {}

    interface Factory {
        fun create(id: LiveChannel.Id): ChannelDetailDelegate
    }
}

class ChannelDetailChannelSection(
    val id: IdBase,
    val position: Int,
    val title: String,
    val content: ChannelDetailContent<*>?,
) {
    sealed class ChannelDetailContent<T> {
        data class MultiPlaylist(override val item: List<LiveVideoThumbnail>) :
            ChannelDetailContent<LiveVideoThumbnail>()

        data class SinglePlaylist(override val item: List<LiveVideoThumbnail>) :
            ChannelDetailContent<LiveVideoThumbnail>()

        data class ChannelList(override val item: List<LiveChannel>) :
            ChannelDetailContent<LiveChannel>()

        abstract val item: List<T>
    }
}

enum class ChannelPage {
    ABOUT, CHANNEL_SECTION, UPLOADED, ACTIVITIES, DEBUG_CHANNEL,
    ;
}
