package com.freshdigitable.yttt

import co.touchlab.kermit.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

        override fun incrementMetric(name: String, value: Long) {
            trace.forEach { it.incrementMetric(name, value) }
        }
    }
}

typealias AppPerformanceSetup = () -> Unit

interface AppTrace {
    val name: String
    fun start()
    fun stop()
    fun putMetric(name: String, value: Long)
    fun incrementMetric(name: String, value: Long)

    companion object : Factory {
        override fun newTrace(name: String): AppTrace = AppTraceImpl(name)
    }

    private class AppTraceImpl(override val name: String) : AppTrace {
        private var time = 0L
        private val metrics = ConcurrentHashMap<String, Long>()
        private val counter = ConcurrentHashMap<String, AtomicLong>()
        override fun start() {
            Logger.i(name) { "start: " }
            time = System.currentTimeMillis()
        }

        override fun stop() {
            val elapsed = System.currentTimeMillis() - time
            val res = (metrics + counter.map { it.key to it.value.get() }).toSortedMap()
            Logger.i(name) { "end: $elapsed [ms]\n$res" }
            metrics.clear()
            counter.clear()
        }

        override fun putMetric(name: String, value: Long) {
            metrics[name] = value
        }

        override fun incrementMetric(name: String, value: Long) {
            val c = counter[name] ?: AtomicLong(0).also { counter[name] = it }
            c.addAndGet(value)
        }
    }

    interface Factory {
        fun newTrace(name: String): AppTrace
    }
}
