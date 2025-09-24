package com.freshdigitable.yttt.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageComposableFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface ChannelDetailEntryPoint {
    val factory: IdBaseClassMap<ChannelDetailPageComposableFactory>
}

private lateinit var factoryEntryPoint: ChannelDetailEntryPoint

@Composable
internal fun requireChannelDetailPageComposableFactory(): IdBaseClassMap<ChannelDetailPageComposableFactory> {
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
