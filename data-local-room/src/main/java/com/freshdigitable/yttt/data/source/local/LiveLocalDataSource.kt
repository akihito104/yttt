package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.source.LiveDataSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LiveLocalDataSource @Inject constructor(
    private val dao: LiveDao,
    private val dateTimeProvider: DateTimeProvider,
) : LiveDataSource {
    override val onAir: Flow<List<LiveTimelineItem>> = dao.watchAllOnAir()
    override val upcoming: Flow<List<LiveTimelineItem>> get() = dao.watchAllUpcoming(dateTimeProvider.now())
    override val freeChat: Flow<List<LiveTimelineItem>> = dao.watchAllFreeChat()
}
