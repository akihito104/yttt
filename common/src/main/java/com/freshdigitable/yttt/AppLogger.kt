package com.freshdigitable.yttt

import co.touchlab.kermit.Logger

fun Any.logV(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.v(throwable = throwable, tag = tag, message = message)
}

fun Any.logD(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.d(throwable = throwable, tag = tag, message = message)
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
    Logger.w(throwable = throwable, tag = tag, message = message)
}

fun Any.logE(
    tag: String = this::class.java.simpleName,
    throwable: Throwable? = null,
    message: () -> String,
) {
    Logger.e(throwable = throwable, tag = tag, message = message)
}

object AppLogger {
    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        Logger.i(throwable = throwable, tag = tag, message = message)
    }
}

typealias AppLoggerSetup = () -> Unit
