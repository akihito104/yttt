package com.freshdigitable.yttt

import co.touchlab.kermit.Logger

fun Any.logV(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.v(tag, throwable, message = message)
}

fun Any.logD(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.d(tag, throwable, message = message)
}

fun Any.logI(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    AppLogger.i(tag, throwable, message)
}

fun Any.logW(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.w(tag, throwable, message = message)
}

fun Any.logE(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.e(tag, throwable, message)
}

object AppLogger {
    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        Logger.i(tag, throwable, message = message)
    }
}

typealias AppLoggerSetup = () -> Unit
