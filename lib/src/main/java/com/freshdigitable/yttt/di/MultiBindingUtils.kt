package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.LivePlatform

typealias LivePlatformMap<T> = Map<LivePlatform, @JvmSuppressWildcards T>

fun <T> ClassMap<LivePlatform, LivePlatform>.toMap(values: ClassMap<LivePlatform, T>): LivePlatformMap<T> =
    map { (clz, p) -> p to checkNotNull(values[clz]) { "values[$clz] is null" } }.toMap()
