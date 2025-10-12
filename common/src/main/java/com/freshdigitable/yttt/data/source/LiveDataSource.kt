package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.LiveTimelineItem
import kotlinx.coroutines.flow.Flow

interface LiveDataSource {
    val onAir: Flow<List<LiveTimelineItem>>
    val upcoming: Flow<List<LiveTimelineItem>>
    val freeChat: Flow<List<LiveTimelineItem>>
}
