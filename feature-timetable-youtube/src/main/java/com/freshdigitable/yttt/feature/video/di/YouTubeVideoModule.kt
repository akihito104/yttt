package com.freshdigitable.yttt.feature.video.di

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.video.FindLiveVideoFromYouTubeUseCase
import com.freshdigitable.yttt.feature.video.FindLiveVideoUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface YouTubeVideoModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeVideo.Id::class)
    fun bindFindLiveVideoFromYouTubeUseCase(useCase: FindLiveVideoFromYouTubeUseCase): FindLiveVideoUseCase
}
