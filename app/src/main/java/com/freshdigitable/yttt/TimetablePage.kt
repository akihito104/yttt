package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import com.freshdigitable.yttt.data.model.LiveVideo

enum class TimetablePage {
    OnAir {
        override val textRes: Int = R.string.tab_onAir
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.onAir
    },
    Upcoming {
        override val textRes: Int = R.string.tab_upcoming
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.upcoming
    },
    ;

    abstract fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>>
    abstract val textRes: Int
}
