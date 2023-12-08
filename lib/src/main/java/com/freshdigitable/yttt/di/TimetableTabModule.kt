package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FetchTimetableItemSourceUseCase
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.TimetablePageDelegate
import com.freshdigitable.yttt.TimetablePageDelegateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

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
    fun bindDelegate(facade: TimetablePageDelegateImpl): TimetablePageDelegate
}
