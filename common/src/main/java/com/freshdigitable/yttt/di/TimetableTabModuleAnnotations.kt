package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.TimetablePage
import dagger.MapKey
import javax.inject.Qualifier

@Suppress("unused")
@Qualifier
annotation class TimetableTabQualifier(val page: TimetablePage)

@Suppress("unused")
@MapKey
annotation class TimetableTabKey(val page: TimetablePage)
