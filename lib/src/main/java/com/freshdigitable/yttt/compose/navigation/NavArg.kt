package com.freshdigitable.yttt.compose.navigation

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveId
import kotlin.reflect.KClass

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

    fun getValue(state: SavedStateHandle): T? {
        return state[argName] ?: if (nullable == false) defaultValue else null
    }

    fun getArgFormat(): String
    fun parsePath(value: T): String = if (value is Enum<*>) value.name else value.toString()

    interface PathParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = null
        override val defaultValue: T? get() = null
        override fun getValue(bundle: Bundle?): T = requireNotNull(super.getValue(bundle))
        override fun getValue(state: SavedStateHandle): T = requireNotNull(super.getValue(state))
        override fun getArgFormat(): String = "{$argName}"

        companion object {
            fun <T> create(argName: String, type: NavType<T>): PathParam<T> =
                PathParamImpl(argName, type)

            fun string(argName: String): PathParam<String> =
                create(argName, NavType.StringType.nonNull())

            inline fun <reified T : Enum<*>> enum(argName: String): PathParam<T> =
                create(argName, NavType.EnumType(T::class.java))
        }
    }

    interface QueryParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = true
        override fun getArgFormat(): String = "$argName={$argName}"
        override fun parsePath(value: T): String = "$argName=${super.parsePath(value)}"

        companion object {
            fun <T> create(
                argName: String,
                type: NavType<T>,
                defaultValue: T? = null,
            ): QueryParam<T> = QueryParamImpl(argName, type, defaultValue)

            fun nonNullString(argName: String, defaultValue: String): QueryParam<String> = create(
                argName = argName,
                type = NavType.StringType.nonNull(),
                defaultValue = defaultValue,
            )

            fun string(argName: String): QueryParam<String?> = create(argName, NavType.StringType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun asNavArg(): NavArg<Any> = this as NavArg<Any>
}

private class PathParamImpl<T>(
    override val argName: String,
    override val type: NavType<T>,
) : NavArg.PathParam<T>

class LiveIdPathParam<T : LiveId> {
    private val valuePath = NavArg.PathParam.string("value")
    private val typePath = NavArg.PathParam.string("platform")
    val params: Array<NavArg.PathParam<String>> = arrayOf(typePath, valuePath)
    fun parseToPathParam(id: T): Array<Pair<NavArg<*>, Any>> =
        arrayOf(typePath to id.type.java.name, valuePath to id.value)

    fun parseToId(savedStateHandle: SavedStateHandle, id: (String, KClass<out IdBase>) -> T): T {
        val type = typePath.getValue(savedStateHandle)
        @Suppress("UNCHECKED_CAST")
        return id(
            valuePath.getValue(savedStateHandle),
            Class.forName(type).kotlin as KClass<out IdBase>,
        )
    }
}

private class QueryParamImpl<T>(
    override val argName: String,
    override val type: NavType<T>,
    override val defaultValue: T? = null,
) : NavArg.QueryParam<T>
