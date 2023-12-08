package com.freshdigitable.yttt

import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.flow.Flow

interface FetchTimetableItemSourceUseCase {
    operator fun invoke(): Flow<List<LiveVideo>>
}
