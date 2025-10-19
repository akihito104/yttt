package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class AppTraceVerifier : TestWatcher() {
    private var started = false
    private var stopped = false
    override fun starting(description: Description?) {
        AppPerformance.addTraceFactory(
            factory = object : AppTrace.Factory {
                override fun newTrace(name: String): AppTrace {
                    return object : AppTrace {
                        override val name: String get() = name

                        override fun start() {
                            started = true
                        }

                        override fun stop() {
                            stopped = true
                        }

                        override fun putMetric(name: String, value: Long) = Unit
                        override fun incrementMetric(name: String, value: Long) = Unit
                    }
                }
            },
        )
    }

    var isTraceable: Boolean = true
    override fun succeeded(description: Description?) {
        if (isTraceable) {
            started.shouldBeTrue()
            stopped.shouldBeTrue()
        } else {
            started.shouldBeFalse()
            stopped.shouldBeFalse()
        }
    }
}
