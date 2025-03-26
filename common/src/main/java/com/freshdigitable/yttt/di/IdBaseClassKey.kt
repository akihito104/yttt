package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LivePlatform
import dagger.MapKey
import javax.inject.Qualifier
import kotlin.reflect.KClass

typealias ClassMap<C, T> = Map<Class<out C>, @JvmSuppressWildcards T>

@MapKey
annotation class IdBaseClassKey(val value: KClass<out IdBase>)

// ksp is not supported nested typealias
typealias IdBaseClassMap<T> = Map<Class<out IdBase>, @JvmSuppressWildcards T>

@MapKey
annotation class LivePlatformKey(val value: KClass<out LivePlatform>)

@Qualifier
annotation class LivePlatformQualifier(val value: KClass<out LivePlatform>)
