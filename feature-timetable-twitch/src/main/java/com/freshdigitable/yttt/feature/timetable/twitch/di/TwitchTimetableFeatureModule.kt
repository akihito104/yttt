package com.freshdigitable.yttt.feature.timetable.twitch.di

import com.freshdigitable.yttt.FetchStreamUseCase
import com.freshdigitable.yttt.FetchTimetableItemSourceUseCase
import com.freshdigitable.yttt.TimetableContextMenuSelector
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.di.TimetableTabQualifier
import com.freshdigitable.yttt.feature.timetable.twitch.FetchTwitchOnAirItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.twitch.FetchTwitchStreamUseCase
import com.freshdigitable.yttt.feature.timetable.twitch.FetchTwitchUpcomingItemSourceUseCase
import com.freshdigitable.yttt.feature.timetable.twitch.TimetableContextMenuDelegateForTwitch
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet

@InstallIn(ViewModelComponent::class)
@Module
internal interface TwitchTimetableFeatureModule {
    @Binds
    @IntoSet
    fun bindTwitchStreamUseCase(useCase: FetchTwitchStreamUseCase): FetchStreamUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchStream.Id::class)
    fun bindTimetableContextMenuDelegateForTwitch(delegate: TimetableContextMenuDelegateForTwitch): TimetableContextMenuSelector

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindTimetableContextMenuDelegateForTwitchChannelSchedule(delegate: TimetableContextMenuDelegateForTwitch): TimetableContextMenuSelector

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.OnAir)
    fun bindFetchTwitchOnAirItemSourceUseCase(useCase: FetchTwitchOnAirItemSourceUseCase): FetchTimetableItemSourceUseCase


    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.Upcoming)
    fun bindFetchTwitchUpcomingItemSourceUseCase(useCase: FetchTwitchUpcomingItemSourceUseCase): FetchTimetableItemSourceUseCase
}
