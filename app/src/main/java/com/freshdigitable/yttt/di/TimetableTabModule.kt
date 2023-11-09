package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FetchTimetableItemSourceUseCase
import com.freshdigitable.yttt.FetchTwitchOnAirItemSourceUseCase
import com.freshdigitable.yttt.FetchTwitchUpcomingItemSourceUseCase
import com.freshdigitable.yttt.FetchYouTubeFreeChatItemSourceUseCase
import com.freshdigitable.yttt.FetchYouTubeOnAirItemSourceUseCase
import com.freshdigitable.yttt.FetchYouTubeUpcomingItemSourceUseCase
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.TimetablePageFacade
import com.freshdigitable.yttt.TimetablePageFacadeImpl
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import javax.inject.Qualifier

@Suppress("unused")
@Qualifier
annotation class TimetableTabQualifier(val page: TimetablePage)

@Suppress("unused")
@MapKey
annotation class TimetableTabKey(val page: TimetablePage)

@Suppress("unused")
@InstallIn(ViewModelComponent::class)
@Module
interface TimetableTabModules {
    @Binds
    @IntoMap
    @TimetableTabKey(TimetablePage.OnAir)
    fun bindOnAirSources(
        @TimetableTabQualifier(TimetablePage.OnAir) source: Set<@JvmSuppressWildcards FetchTimetableItemSourceUseCase>,
    ): Set<FetchTimetableItemSourceUseCase>

    @Binds
    @IntoMap
    @TimetableTabKey(TimetablePage.Upcoming)
    fun bindUpcomingSources(
        @TimetableTabQualifier(TimetablePage.Upcoming) source: Set<@JvmSuppressWildcards FetchTimetableItemSourceUseCase>,
    ): Set<FetchTimetableItemSourceUseCase>

    @Binds
    @IntoMap
    @TimetableTabKey(TimetablePage.FreeChat)
    fun bindFreeChatSources(
        @TimetableTabQualifier(TimetablePage.FreeChat) source: Set<@JvmSuppressWildcards FetchTimetableItemSourceUseCase>,
    ): Set<FetchTimetableItemSourceUseCase>

    @Binds
    fun bindFacade(facade: TimetablePageFacadeImpl): TimetablePageFacade

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.OnAir)
    fun bindFetchTwitchOnAirItemSourceUseCase(useCase: FetchTwitchOnAirItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.OnAir)
    fun bindFetchYouTubeOnAirItemSourceUseCase(useCase: FetchYouTubeOnAirItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.Upcoming)
    fun bindFetchTwitchUpcomingItemSourceUseCase(useCase: FetchTwitchUpcomingItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.Upcoming)
    fun bindFetchYouTubeUpcomingItemSourceUseCase(useCase: FetchYouTubeUpcomingItemSourceUseCase): FetchTimetableItemSourceUseCase

    @Binds
    @IntoSet
    @TimetableTabQualifier(TimetablePage.FreeChat)
    fun bindFetchYouTubeFreeChatItemSourceUseCase(useCase: FetchYouTubeFreeChatItemSourceUseCase): FetchTimetableItemSourceUseCase
}
