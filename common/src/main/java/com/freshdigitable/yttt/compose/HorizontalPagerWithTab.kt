package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlinx.coroutines.flow.Flow

interface TabData<T : TabData<T>> : Comparable<T> {
    @Composable
    @ReadOnlyComposable
    fun title(): String
    override fun compareTo(other: T): Int
}

interface HorizontalPagerTabViewModel<T : TabData<T>> {
    val tabData: Flow<List<T>>
    val initialTab: List<T>
}
