package com.freshdigitable.yttt.lib

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.text.DecimalFormat

class StudyBigDecimalTest : ShouldSpec({
    should("calculate precision correctly") {
        BigInteger("20").toBigDecimal().precision() shouldBe 2
        BigInteger("200").toBigDecimal().precision() shouldBe 3
    }

    should("format moved point BigDecimals correctly with DecimalFormat") {
        val formatter = DecimalFormat("#.##")

        formatter.format(BigInteger("1200").toBigDecimal().movePointLeft(3)) shouldBe "1.2"
        formatter.format(BigInteger("1250").toBigDecimal().movePointLeft(3)) shouldBe "1.25"
        formatter.format(BigInteger("12500").toBigDecimal().movePointLeft(3)) shouldBe "12.5"
        formatter.format(BigInteger("125000").toBigDecimal().movePointLeft(3)) shouldBe "125"
    }
})
