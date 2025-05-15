package com.freshdigitable.yttt.feature.channel

import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LinkAnnotationDialogState
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

interface ChannelDetailDelegate {
    val tabs: List<ChannelDetailPageTab<*>>
    val channelDetailBody: Flow<LiveChannelDetailBody?>
    val pagerContent: PagerContent
    suspend fun clearForDetail() {}

    interface Factory {
        fun create(
            id: LiveChannel.Id,
            coroutineScope: CoroutineScope,
            errorMessageChannel: SendChannel<SnackbarVisuals>,
        ): ChannelDetailDelegate
    }

    interface PagerContent {
        val annotatedDetail: Flow<AnnotatableString>
    }
}

interface ChannelDetailPageScope {
    val pagerContent: ChannelDetailDelegate.PagerContent
    val dialogState: LinkAnnotationDialogState
    fun annotatedText(textProvider: () -> AnnotatableString): @Composable () -> Unit
    fun <T> list(
        itemProvider: () -> List<T>,
        idProvider: (T) -> IdBase,
        content: @Composable (T) -> Unit,
    ): @Composable () -> Unit

    fun videoItem(url: String, title: String): @Composable () -> Unit

    companion object
}

interface ChannelDetailPageTab<T : ChannelDetailPageTab<T>> : TabData<T>

typealias ChannelDetailPageComposable = @Composable ChannelDetailPageScope.() -> Unit

interface ChannelDetailPageComposableFactory {
    fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable
}
