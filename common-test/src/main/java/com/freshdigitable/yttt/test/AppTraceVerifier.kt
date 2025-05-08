package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.google.common.truth.Truth.assertThat
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class AppTraceVerifier : TestWatcher() {
    private var started = false
    private var stopped = false
    override fun starting(description: Description?) {
        AppPerformance.addTraceFactory(factory = object : AppTrace.Factory {
            override fun newTrace(name: String): AppTrace {
                return object : AppTrace {
                    override val name: String get() = name

                    override fun start() {
                        started = true
                    }

                    override fun stop() {
                        stopped = true
                    }

                    override fun putMetric(name: String, value: Long) {}
                    override fun incrementMetric(name: String, value: Long) {}
                }
            }
        })
    }

    var isTraceable: Boolean = true
    override fun finished(description: Description?) {
        if (isTraceable) {
            assertThat(started).isTrue()
            assertThat(stopped).isTrue()
        } else {
            assertThat(started).isFalse()
            assertThat(stopped).isFalse()
        }
    }
}
