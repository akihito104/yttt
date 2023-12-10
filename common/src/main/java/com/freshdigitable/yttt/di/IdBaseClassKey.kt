package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.IdBase
import dagger.MapKey
import kotlin.reflect.KClass

@MapKey
annotation class IdBaseClassKey(val value: KClass<out IdBase>)

typealias IdBaseClassMap<T> = Map<Class<out IdBase>, @JvmSuppressWildcards T>
