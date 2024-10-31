package com.freshdigitable.yttt

import co.touchlab.kermit.Logger

interface AppPerformance {
    fun newTrace(name: String): AppTrace

    companion object {
        fun create(): AppPerformance = AppPerformanceImpl()
    }

    private class AppPerformanceImpl : AppPerformance {
        override fun newTrace(name: String): AppTrace = AppTrace.newTrace(name)
    }
}

interface AppTrace {
    val name: String
    fun start()
    fun stop()

    companion object {
        internal fun newTrace(name: String): AppTrace = AppTraceImpl(name)
    }

    private class AppTraceImpl(override val name: String) : AppTrace {
        private var time = 0L
        override fun start() {
            Logger.i(name) { "start: " }
            time = System.currentTimeMillis()
        }

        override fun stop() {
            val elapsed = System.currentTimeMillis() - time
            Logger.i(name) { "end: $elapsed [ms]" }
        }
    }
}
