package com.freshdigitable.yttt.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface YtttWidgetEntryPoint {
    fun stateFactory(): YtttState.Factory
}
