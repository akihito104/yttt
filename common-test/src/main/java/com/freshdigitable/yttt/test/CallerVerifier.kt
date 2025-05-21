package com.freshdigitable.yttt.test

import com.google.common.truth.Truth.assertWithMessage
import org.junit.rules.Verifier

class CallerVerifier : Verifier() {
    private val recorder = mutableListOf<RecordableFunction<*, *>>()
    fun <T, R> wrap(expected: Int, body: (T) -> R): (T) -> R {
        val spy = RecordableFunction.spy(expected, body)
        recorder.add(spy)
        return spy
    }

    override fun verify() {
        recorder.run {
            forEach { assertWithMessage(it.toString()).that(it.actual).isEqualTo(it.expected) }
            clear()
        }
    }
}

internal interface RecordableFunction<T, R> : (T) -> R {
    val actual: Int
    val expected: Int

    companion object {
        fun <T, R> spy(expected: Int, body: (T) -> R): RecordableFunction<T, R> =
            Impl(expected, body)
    }

    private class Impl<T, R>(
        override val expected: Int,
        private val body: (T) -> R,
    ) : RecordableFunction<T, R> {
        override var actual: Int = 0
            private set

        override fun invoke(p1: T): R {
            actual++
            return body(p1)
        }

        override fun toString(): String = body.toString()
    }
}
