package com.freshdigitable.yttt.widget

import com.freshdigitable.yttt.feature.video.FetchPinnedVideoUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface YtttWidgetEntryPoint {
    fun fetchPinnedVideoUseCase(): FetchPinnedVideoUseCase
}
