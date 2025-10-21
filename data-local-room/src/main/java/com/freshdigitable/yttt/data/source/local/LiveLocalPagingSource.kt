package com.freshdigitable.yttt.data.source.local

import androidx.paging.PagingSource
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LiveLocalPagingSource @Inject constructor(
    private val dao: LiveDao,
) : LiveDataPagingSource {
    override val onAir: PagingSource<Int, out LiveVideo> get() = dao.getAllOnAirPagingSource()
    override fun upcoming(current: Instant): PagingSource<Int, out LiveVideo> =
        dao.getAllUpcomingPagingSource(current)

    override val freeChat: PagingSource<Int, out LiveVideo> get() = dao.getAllFreeChatPagingSource()
}
