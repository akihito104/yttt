package com.freshdigitable.yttt

import co.touchlab.kermit.Logger

abstract class AppPerformance(factory: List<AppTrace.Factory>) : AppTrace.Factory {
    @Volatile
    private var _traceFactory: List<AppTrace.Factory> = factory
    var traceFactory: List<AppTrace.Factory>
        get() = _traceFactory
        set(value) {
            synchronized(this) {
                _traceFactory = value
            }
        }

    companion object : AppPerformance(listOf(AppTrace.Companion)) {
        fun addTraceFactory(factory: AppTrace.Factory) {
            traceFactory = traceFactory + factory
        }

        override fun newTrace(name: String): AppTrace {
            val t = traceFactory.map { it.newTrace(name) }
            return AppTraceWrapper(name, t)
        }
    }

    private class AppTraceWrapper(
        override val name: String,
        private val trace: Collection<AppTrace>,
    ) : AppTrace {
        override fun start() {
            trace.forEach { it.start() }
        }

        override fun stop() {
            trace.forEach { it.stop() }
        }

    }
}

typealias AppPerformanceSetup = () -> Unit

interface AppTrace {
    val name: String
    fun start()
    fun stop()

    companion object : Factory {
        override fun newTrace(name: String): AppTrace = AppTraceImpl(name)
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

    interface Factory {
        fun newTrace(name: String): AppTrace
    }
}
