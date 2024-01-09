package com.freshdigitable.yttt.data.model

import androidx.annotation.ColorInt
import kotlin.reflect.KClass

interface IdBase {
    val value: String
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface LivePlatform {
    val name: String

    @get:ColorInt
    val color: Long
}

interface LiveId : IdBase {
    val type: KClass<*>
}
