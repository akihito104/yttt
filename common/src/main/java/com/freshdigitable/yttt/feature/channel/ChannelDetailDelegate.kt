package com.freshdigitable.yttt.feature.channel

import androidx.compose.runtime.Composable
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LinkAnnotationDialogState
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import kotlinx.coroutines.flow.Flow

interface ChannelDetailDelegate {
    val tabs: List<ChannelDetailPageTab<*>>
    val channelDetailBody: Flow<LiveChannelDetailBody?>
    val pagerContent: PagerContent
    suspend fun clearForDetail() {}

    interface Factory {
        fun create(id: LiveChannel.Id): ChannelDetailDelegate
    }

    interface PagerContent {
        val annotatedDetail: Flow<AnnotatableString>
    }
}

interface ChannelDetailPageScope {
    val delegate: ChannelDetailDelegate
    val dialogState: LinkAnnotationDialogState
    fun annotatedText(textProvider: () -> AnnotatableString): @Composable () -> Unit
    fun <T> list(
        itemProvider: () -> List<T>,
        idProvider: (T) -> IdBase,
        content: @Composable (T) -> Unit,
    ): @Composable () -> Unit

    fun videoItem(item: LiveVideoThumbnail): @Composable () -> Unit

    companion object
}

interface ChannelDetailPageTab<T : ChannelDetailPageTab<T>> : TabData<T>

typealias ChannelDetailPageComposable = @Composable ChannelDetailPageScope.() -> Unit

interface ChannelDetailPageComposableFactory {
    fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable
}
