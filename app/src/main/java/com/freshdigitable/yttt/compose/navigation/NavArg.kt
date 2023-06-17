package com.freshdigitable.yttt.compose.navigation

import android.os.Bundle
import androidx.navigation.NavType

interface NavArg<T> {
    val argName: String
    val type: NavType<T>
    val nullable: Boolean?
    val defaultValue: T?

    fun getValue(bundle: Bundle?): T? {
        if (bundle == null) {
            return if (nullable == false) defaultValue else null
        }
        return type[bundle, argName] ?: if (nullable == false) defaultValue else null
    }

    fun getArgFormat(): String
    fun parsePath(value: T): String = if (value is Enum<*>) value.name else value.toString()

    interface PathParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = null
        override val defaultValue: T? get() = null
        override fun getValue(bundle: Bundle?): T = requireNotNull(super.getValue(bundle))
        override fun getArgFormat(): String = "{$argName}"
    }

    interface QueryParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = true
        override fun getArgFormat(): String = "$argName={$argName}"
        override fun parsePath(value: T): String = "$argName=${super.parsePath(value)}"
    }

    @Suppress("UNCHECKED_CAST")
    fun asNavArg(): NavArg<Any> = this as NavArg<Any>
}
