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

        override fun putMetric(name: String, value: Long) {
            trace.forEach { it.putMetric(name, value) }
        }
    }
}

typealias AppPerformanceSetup = () -> Unit

interface AppTrace {
    val name: String
    fun start()
    fun stop()
    fun putMetric(name: String, value: Long)

    companion object : Factory {
        override fun newTrace(name: String): AppTrace = AppTraceImpl(name)
    }

    private class AppTraceImpl(override val name: String) : AppTrace {
        private var time = 0L
        private val metrics = mutableMapOf<String, Long>()
        override fun start() {
            Logger.i(name) { "start: " }
            time = System.currentTimeMillis()
        }

        override fun stop() {
            val elapsed = System.currentTimeMillis() - time
            Logger.i(name) { "end: $elapsed [ms]\n$metrics" }
        }

        override fun putMetric(name: String, value: Long) {
            metrics[name] = value
        }
    }

    interface Factory {
        fun newTrace(name: String): AppTrace
    }
}
