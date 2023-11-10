package com.freshdigitable.yttt

enum class TimetablePage(val type: Type = Type.SIMPLE) {
    OnAir {
        override val textRes: Int = R.string.tab_onAir
    },
    Upcoming(type = Type.GROUPED) {
        override val textRes: Int = R.string.tab_upcoming
    },
    FreeChat {
        override val textRes: Int = R.string.tab_freechat
    }
    ;

    abstract val textRes: Int

    enum class Type { SIMPLE, GROUPED, }
}
