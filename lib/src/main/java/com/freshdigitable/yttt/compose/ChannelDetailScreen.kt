package com.freshdigitable.yttt.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LinkAnnotationDialogState
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageComposable
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageComposableFactory
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageScope
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageTab
import com.freshdigitable.yttt.feature.channel.ChannelViewModel
import com.freshdigitable.yttt.logD
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface ChannelDetailEntryPoint {
    val factory: IdBaseClassMap<ChannelDetailPageComposableFactory>
}

private lateinit var factoryEntryPoint: ChannelDetailEntryPoint

@Composable
private fun requireChannelDetailPageComposableFactory(): IdBaseClassMap<ChannelDetailPageComposableFactory> {
    if (!::factoryEntryPoint.isInitialized) {
        factoryEntryPoint =
            EntryPointAccessors.fromActivity<ChannelDetailEntryPoint>(LocalContext.current.getActivity())
    }
    return factoryEntryPoint.factory
}

private fun Context.getActivity(): Activity {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("activity is not found.")
}

@Composable
fun ChannelDetailScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
    channelId: LiveChannel.Id,
    pageFactory: IdBaseClassMap<ChannelDetailPageComposableFactory> = requireChannelDetailPageComposableFactory(),
) {
    AppLogger.logD("ChannelDetail") { "start:" }
    val detail = viewModel.channelDetailBody.collectAsState()
    val dialog = remember { LinkAnnotationDialogState() }
    val scope = remember(viewModel.pagerContent, dialog) {
        ChannelDetailPageScope.create(viewModel.pagerContent, dialog)
    }
    val composableFactory = checkNotNull(pageFactory[channelId.type.java])
    ChannelDetailScreen(
        channelDetail = { detail.value },
        pages = viewModel.tabs.associateWith { composableFactory.create(it) },
        pageScope = scope,
    )
    LinkAnnotationDialog(state = dialog)
}

@Composable
private fun ChannelDetailScreen(
    channelDetail: () -> LiveChannelDetailBody?,
    pages: Map<ChannelDetailPageTab<*>, ChannelDetailPageComposable> = emptyMap(),
    pageScope: ChannelDetailPageScope,
) {
    Column(Modifier.fillMaxSize()) {
        ChannelDetailHeader(channelDetail)
        ChannelDetailPager(pages.keys.toList()) { page ->
            AppLogger.logD("ChannelDetail") { "page:$page" }
            checkNotNull(pages[page])(pageScope)
        }
    }
}

@Composable
private fun ChannelDetailHeader(
    channelDetailProvider: () -> LiveChannelDetailBody?,
) {
    val channelDetail = channelDetailProvider() ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bannerUrl = channelDetail.bannerUrl
        if (bannerUrl?.isNotEmpty() == true) {
            ImageLoadableView.ChannelArt(url = bannerUrl)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            LiveChannelIcon(
                iconUrl = channelDetail.iconUrl,
                iconSize = 56.dp,
            )
            Text(
                text = channelDetail.title,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
            )
            Text(
                text = channelDetail.statsText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ChannelDetailPager(
    pages: List<ChannelDetailPageTab<*>>,
    pageContent: @Composable PagerScope.(ChannelDetailPageTab<*>) -> Unit,
) {
    HorizontalPagerWithTabScreen(
        edgePadding = 0.dp,
        tabCount = pages.size,
        tab = { pages[it].title() },
    ) {
        pageContent(pages[it])
    }
}

private fun ChannelDetailPageScope.Companion.create(
    pagerContent: ChannelDetailDelegate.PagerContent,
    dialogState: LinkAnnotationDialogState,
): ChannelDetailPageScope = object : ChannelDetailPageScope {
    override val pagerContent: ChannelDetailDelegate.PagerContent get() = pagerContent
    override val dialogState: LinkAnnotationDialogState get() = dialogState
    override fun annotatedText(textProvider: () -> AnnotatableString): @Composable () -> Unit = {
        AnnotatedTextPage(textProvider = textProvider, dialog = dialogState)
    }

    override fun <T> list(
        itemProvider: () -> List<T>,
        idProvider: (T) -> IdBase,
        content: @Composable (T) -> Unit
    ): @Composable () -> Unit = {
        PlainListPage(listProvider = itemProvider, idProvider = idProvider, content = content)
    }

    override fun videoItem(url: String, title: String): @Composable () -> Unit = {
        VideoListItem(thumbnailUrl = url, title = title)
    }
}

@Composable
private fun AnnotatedTextPage(
    textProvider: () -> AnnotatableString,
    dialog: LinkAnnotationDialogState,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        AnnotatableText(
            fontSize = 14.sp,
            annotatableString = textProvider(),
            dialog = dialog,
        )
    }
}

@Composable
private fun <T> PlainListPage(
    listProvider: () -> List<T>,
    idProvider: (T) -> IdBase,
    content: @Composable (T) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            itemsIndexed(
                items = listProvider(),
                key = { _, item -> idProvider(item).value },
            ) { _, item -> content(item) }
        },
    )
}

@Composable
private fun VideoListItem(
    thumbnailUrl: String,
    title: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.5f)
                .aspectRatio(16f / 9f)
                .align(Alignment.Top),
        ) {
            ImageLoadableView.Thumbnail(
                url = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            fontSize = 14.sp,
        )
    }
}

@LightDarkModePreview
@Composable
private fun ChannelScreenPreview() {
    val channelDetail = object : LiveChannelDetailBody {
        override val id: LiveChannel.Id get() = LiveChannel.Id("a", LiveChannel.Id::class)
        override val title: String = "channel title"
        override val statsText: String get() = "@custom_url・Subscribers:52.4k・Videos:132・Views:38,498,283・Published:2021/04/13"
        override val bannerUrl: String? get() = null
        override val iconUrl: String get() = ""
        override val platform: LivePlatform
            get() = object : LivePlatform {
                override val name: String get() = "platform"
                override val color: Long get() = 0xFF000000
            }

        override fun equals(other: Any?): Boolean = throw NotImplementedError()
        override fun hashCode(): Int = throw NotImplementedError()
    }
    val pageScope = ChannelDetailPageScope.create(
        pagerContent = object : ChannelDetailDelegate.PagerContent {
            override val annotatedDetail: Flow<AnnotatableString> = flowOf(
                AnnotatableString.create(
                    annotatable = "text.",
                    accountUrlCreator = { emptyList() },
                ),
            )
        },
        dialogState = LinkAnnotationDialogState(),
    )
    AppTheme {
        ChannelDetailScreen(
            channelDetail = { channelDetail },
            pageScope = pageScope,
            pages = mapOf(
                Tab.ABOUT to { Text("text is here.") },
                Tab.VIDEO to { Text("video is here.") },
            ),
        )
    }
}

@LightDarkModePreview
@Composable
private fun LazyColumnPreview() {
    AppTheme {
        PlainListPage(
            listProvider = {
                listOf("a", "b", "c").mapIndexed { i, title ->
                    LiveVideoThumbnailEntity(
                        id = LiveVideo.Id("video_$i", LiveVideo.Id::class),
                        title = title,
                        thumbnailUrl = "",
                    )
                }
            },
            idProvider = { it.id },
            content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
        )
    }
}

private data class LiveVideoThumbnailEntity(
    override val id: LiveVideo.Id,
    override val title: String,
    override val thumbnailUrl: String,
) : LiveVideoThumbnail

private enum class Tab : ChannelDetailPageTab<Tab> {
    ABOUT, VIDEO;

    @Composable
    override fun title(): String = name
}
