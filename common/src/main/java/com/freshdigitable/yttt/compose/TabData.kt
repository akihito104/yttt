package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

interface TabData<T : TabData<T>> : Comparable<T> {
    @Composable
    @ReadOnlyComposable
    fun title(): String
    override fun compareTo(other: T): Int
}
