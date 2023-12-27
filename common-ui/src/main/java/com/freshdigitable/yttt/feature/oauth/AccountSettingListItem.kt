package com.freshdigitable.yttt.feature.oauth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.LivePlatform

interface AccountSettingListItem {
    val platform: LivePlatform

    @Composable
    fun label(): String = platform.name

    @Composable
    fun ListBodyContent(listItem: @Composable (ListBody) -> Unit)

    @Immutable
    class ListBody(
        val title: String,
        val enabled: () -> Boolean,
        val buttonText: @Composable () -> String,
        val onClick: () -> Unit,
    )
}
