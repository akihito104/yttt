package com.freshdigitable.yttt.data.model

interface IdBase<S> {
    val value: S
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
