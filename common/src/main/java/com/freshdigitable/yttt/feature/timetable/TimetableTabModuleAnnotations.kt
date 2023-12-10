package com.freshdigitable.yttt.feature.timetable

import dagger.MapKey
import javax.inject.Qualifier

@Suppress("unused")
@Qualifier
annotation class TimetableTabQualifier(val page: TimetablePage)

@Suppress("unused")
@MapKey
annotation class TimetableTabKey(val page: TimetablePage)
