package com.freshdigitable.yttt.data.model

interface LiveChannel {
    val id: Id
    val title: String

    data class Id(val value: String)
}
