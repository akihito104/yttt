package com.freshdigitable.yttt

enum class TimetablePage {
    OnAir {
        override val textRes: Int = R.string.tab_onAir
    },
    Upcoming {
        override val textRes: Int = R.string.tab_upcoming
    },
    ;

    abstract val textRes: Int
}
