package com.freshdigitable.yttt.feature.timetable

enum class TimetablePage(val type: Type = Type.SIMPLE) {
    OnAir,
    Upcoming(type = Type.GROUPED),
    FreeChat,
    ;

    enum class Type { SIMPLE, GROUPED, }
}
