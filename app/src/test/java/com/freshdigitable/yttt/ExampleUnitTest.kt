package com.freshdigitable.yttt

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.text.DecimalFormat

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun studyBigDecimal() {
        assertEquals(2, BigInteger("20").toBigDecimal().precision())
        assertEquals(3, BigInteger("200").toBigDecimal().precision())

        assertEquals(
            "1.2",
            DecimalFormat("#.##").format(BigInteger("1200").toBigDecimal().movePointLeft(3))
        )
        assertEquals(
            "1.25",
            DecimalFormat("#.##").format(BigInteger("1250").toBigDecimal().movePointLeft(3))
        )
        assertEquals(
            "12.5",
            DecimalFormat("#.##").format(BigInteger("12500").toBigDecimal().movePointLeft(3))
        )
        assertEquals(
            "125",
            DecimalFormat("#.##").format(BigInteger("125000").toBigDecimal().movePointLeft(3))
        )
    }
}
