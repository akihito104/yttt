package com.freshdigitable.yttt.feature.timetable.di

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.timetable.FetchStreamUseCase
import com.freshdigitable.yttt.feature.timetable.FetchTwitchStreamUseCase
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegateForTwitch
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuSelector
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
    fun bindTimetableContextMenuDelegateForTwitch(
        delegate: TimetableContextMenuDelegateForTwitch,
    ): TimetableContextMenuSelector

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindTimetableContextMenuDelegateForTwitchChannelSchedule(
        delegate: TimetableContextMenuDelegateForTwitch,
    ): TimetableContextMenuSelector
}
