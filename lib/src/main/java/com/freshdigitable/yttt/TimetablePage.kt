package com.freshdigitable.yttt

import com.freshdigitable.yttt.lib.R

internal val TimetablePage.textRes: Int
    get() {
        return when (this) {
            TimetablePage.OnAir -> R.string.tab_onAir
            TimetablePage.Upcoming -> R.string.tab_upcoming
            TimetablePage.FreeChat -> R.string.tab_freechat
        }
    }
