package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.feature.timetable.TimetablePageDelegate
import com.freshdigitable.yttt.feature.timetable.TimetablePageDelegateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Suppress("unused")
@InstallIn(ViewModelComponent::class)
@Module
internal interface TimetableTabModule {
    @Binds
    fun bindDelegate(facade: TimetablePageDelegateImpl): TimetablePageDelegate
}
