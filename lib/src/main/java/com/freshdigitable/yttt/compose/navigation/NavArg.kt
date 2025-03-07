package com.freshdigitable.yttt.compose.navigation

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.freshdigitable.yttt.compose.navigation.NavArgValue.Companion.withValue
import com.freshdigitable.yttt.compose.navigation.NavContent.Scope
import com.freshdigitable.yttt.data.model.LiveId
import kotlin.reflect.KClass

internal fun <T> Bundle.getValue(arg: NavArg<T>): T? =
    arg.type[this, arg.argName] ?: if (arg.nullable == false) arg.defaultValue else null

internal fun <T> SavedStateHandle.getValue(arg: NavArg<T>): T? =
    this[arg.argName] ?: if (arg.nullable == false) arg.defaultValue else null

interface NavArg<T> {
    val argName: String
    val type: NavType<T>
    val nullable: Boolean?
    val defaultValue: T?

    fun getArgFormat(): String
    fun parsePath(value: T): String = if (value is Enum<*>) value.name else value.toString()

    interface PathArg<T> : NavArg<T> {
        override val nullable: Boolean? get() = null
        override val defaultValue: T? get() = null
        override fun getArgFormat(): String = "{$argName}"

        companion object {
            fun <T> create(argName: String, type: NavType<T>): PathArg<T> =
                PathArgImpl(argName, type)

            fun string(argName: String): PathArg<String> =
                create(argName, NavType.StringType.nonNull())

            inline fun <reified T : Enum<*>> enum(argName: String): PathArg<T> =
                create(argName, NavType.EnumType(T::class.java))
        }
    }

    interface QueryArg<T> : NavArg<T> {
        override val nullable: Boolean? get() = true
        override fun getArgFormat(): String = "$argName={$argName}"
        override fun parsePath(value: T): String = "$argName=${super.parsePath(value)}"

        companion object {
            fun <T> create(
                argName: String,
                type: NavType<T>,
                defaultValue: T? = null,
            ): QueryArg<T> = QueryArgImpl(argName, type, defaultValue)

            fun nonNullString(argName: String, defaultValue: String): QueryArg<String> = create(
                argName = argName,
                type = NavType.StringType.nonNull(),
                defaultValue = defaultValue,
            )

            fun string(argName: String): QueryArg<String?> = create(argName, NavType.StringType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun asNavArg(): NavArg<Any> = this as NavArg<Any>
}

private class PathArgImpl<T>(
    override val argName: String,
    override val type: NavType<T>,
) : NavArg.PathArg<T>

private class QueryArgImpl<T>(
    override val argName: String,
    override val type: NavType<T>,
    override val defaultValue: T? = null,
) : NavArg.QueryArg<T>

class NavArgValue<T>(
    val arg: NavArg<T>,
    val value: T,
) {
    val routeString: String get() = arg.parsePath(value)

    companion object {
        fun <T> NavArg<T>.withValue(value: T): NavArgValue<T> = NavArgValue(this, value)
    }
}

interface NavParam {
    val root: String
    val args: Array<out NavArg<*>> get() = emptyArray()
    val deepLinks: List<NavDeepLink> get() = emptyList()

    interface LiveIdPath<T : LiveId> : NavParam {
        companion object {
            private val valuePath = NavArg.PathArg.string("value")
            private val typePath = NavArg.PathArg.string("platform")
            private val params: Array<NavArg.PathArg<String>> = arrayOf(typePath, valuePath)
            fun <T : LiveId> SavedStateHandle.getLiveId(body: (String, KClass<T>) -> T): T {
                val value = checkNotNull(getValue(valuePath))
                val type = checkNotNull(getValue(typePath))
                @Suppress("UNCHECKED_CAST")
                return body(value, Class.forName(type).kotlin as KClass<T>)
            }
        }

        override val args: Array<out NavArg<*>> get() = params
        fun route(id: T): String =
            route(typePath.withValue(id.type.java.name), valuePath.withValue(id.value))

        fun getLiveId(savedState: SavedStateHandle, body: (String, KClass<T>) -> T): T =
            savedState.getLiveId(body)
    }

    companion object {
        val NavParam.routeFormat: String
            get() {
                val pathArgs = args.filterIsInstance<NavArg.PathArg<Any>>()
                val pp = pathArgs.joinToString("/") { it.getArgFormat() }
                val p = listOfNotNull(root, pp.ifEmpty { null }).joinToString("/")
                val queryArgs = args.filterIsInstance<NavArg.QueryArg<Any>>()
                val q = queryArgs.joinToString("&") { it.getArgFormat() }
                return listOfNotNull(p, q.ifEmpty { null }).joinToString("?")
            }

        fun NavParam.route(vararg values: NavArgValue<*>): String {
            val t = values.associateBy { it.arg }
            val pp = args.filterIsInstance<NavArg.PathArg<*>>().joinToString("/") { p ->
                checkNotNull(t[p]).routeString
            }
            val p = listOfNotNull(root, pp.ifEmpty { null }).joinToString("/")
            val qp = args.filterIsInstance<NavArg.QueryArg<*>>()
            val q = if (qp.isEmpty()) {
                null
            } else {
                qp.mapNotNull { t[it] }.joinToString("&") { it.routeString }
            }
            return listOfNotNull(p, if (q.isNullOrEmpty()) null else q).joinToString("?")
        }
    }
}

typealias ScopedNavContent = @Composable Scope.(NavBackStackEntry) -> Unit

interface NavContent {
    interface Scope : AnimatedVisibilityScope {
        val navController: NavHostController
        val topAppBarState: TopAppBarStateHolder?

        @OptIn(ExperimentalSharedTransitionApi::class)
        val sharedTransition: SharedTransitionScope?

        companion object {
            @OptIn(ExperimentalSharedTransitionApi::class)
            inline fun Scope.asAnimatedSharedTransitionScope(
                content: AnimatedSharedTransitionScope.() -> Unit,
            ) {
                AnimatedSharedTransitionScope(requireNotNull(sharedTransition), this).content()
            }
        }
    }

    fun body(): ScopedNavContent
}

@OptIn(ExperimentalSharedTransitionApi::class)
class AnimatedSharedTransitionScope(
    private val sharedTransition: SharedTransitionScope,
    private val animatedContent: AnimatedVisibilityScope,
) : SharedTransitionScope by sharedTransition, AnimatedVisibilityScope by animatedContent
