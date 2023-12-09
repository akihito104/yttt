package com.freshdigitable.yttt.feature.timetable.di

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.timetable.FetchStreamUseCase
import com.freshdigitable.yttt.feature.timetable.FetchTimetableItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuSelector
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.feature.timetable.TimetableTabQualifier
import com.freshdigitable.yttt.feature.timetable.FetchYouTubeFreeChatItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.FetchYouTubeOnAirItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.FetchYouTubeStreamUseCase
import com.freshdigitable.yttt.feature.timetable.FetchYouTubeUpcomingItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegateForYouTube
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet

@InstallIn(ViewModelComponent::class)
@Module
internal interface YouTubeTimetableFeatureModule {
    @Binds
    @IntoSet
    fun bindYouTubeStreamUseCase(useCase: FetchYouTubeStreamUseCase): FetchStreamUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeVideo.Id::class)
    fun bindTimetableContextMenuDelegateForYouTube(delegate: TimetableContextMenuDelegateForYouTube): TimetableContextMenuSelector

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.OnAir)
    fun bindFetchYouTubeOnAirItemSourceUseCase(useCase: FetchYouTubeOnAirItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.Upcoming)
    fun bindFetchYouTubeUpcomingItemSourceUseCase(useCase: FetchYouTubeUpcomingItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.FreeChat)
    fun bindFetchYouTubeFreeChatItemSourceUseCase(useCase: FetchYouTubeFreeChatItemSourceUseCase): FetchTimetableItemSourceUseCase
}
