package com.freshdigitable.yttt.data.model

import kotlin.reflect.KClass

interface IdBase {
    val value: String
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface LivePlatform {
    val name: String
}

interface LiveId : IdBase {
    val type: KClass<*>
}
