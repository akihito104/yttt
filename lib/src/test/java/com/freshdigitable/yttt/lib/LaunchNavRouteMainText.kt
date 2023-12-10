package com.freshdigitable.yttt.lib

import com.freshdigitable.yttt.compose.LaunchNavRoute
import junit.framework.TestCase.assertEquals
import org.junit.Test

class LaunchNavRouteMainText {
    @Test
    fun testDestination() {
        assertEquals("main?dest={dest}", LaunchNavRoute.Main.route)
        val actual = LaunchNavRoute.Main.parseRoute(LaunchNavRoute.Main.Destinations.TIMETABLE)
        assertEquals("main?dest=TIMETABLE", actual)
    }
}
