package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LivePlatform
import dagger.MapKey
import kotlin.reflect.KClass

typealias ClassMap<C, T> = Map<Class<out C>, @JvmSuppressWildcards T>

@MapKey
annotation class IdBaseClassKey(val value: KClass<out IdBase>)

typealias IdBaseClassMap<T> = ClassMap<IdBase, T>

@MapKey
annotation class LivePlatformKey(val value: KClass<out LivePlatform>)
