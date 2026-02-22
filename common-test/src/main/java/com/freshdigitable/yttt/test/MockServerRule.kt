package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.di.OkHttpModule
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.internal.closeQuietly
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.net.URL
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MockServerRule(private val onServerStart: (URL) -> Unit) : TestWatcher() {
    companion object {
        private val ZONE_ID_GMT: ZoneId = ZoneId.of("GMT")
        private val currentDate: String
            get() = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZONE_ID_GMT)
                .format(FakeDateTimeProviderModule.instant)
    }

    private val server = MockWebServer()
    override fun starting(description: Description) {
        OkHttpModule.logLevel = HttpLoggingInterceptor.Level.NONE
        server.start()
        val serverUrl = server.url("").toUrl()
        onServerStart(serverUrl)
    }


    fun setClient(dispatcher: TestDispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val req = RequestImpl(request)
                val res = dispatcher.dispatch(req).also {
                    if (isLogging) reqRes.add(request to it)
                }
                return MockResponse.Builder()
                    .code(res.statusCode)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("date", currentDate)
                    .body(res.toString())
                    .build()
            }
        }
    }

    var isLogging: Boolean = false
    private val reqRes: MutableList<Pair<RecordedRequest, ResponseJson>> = mutableListOf()
    fun findRecordedResponse(path: String): List<Pair<String, ResponseJson>> {
        return reqRes.filter { it.first.url.encodedPath == path }.map { it.first.url.encodedPath to it.second }
    }

    override fun finished(description: Description) {
        server.closeQuietly()
    }
}

interface TestDispatcher {
    fun dispatch(request: Request): ResponseJson
    interface Request {
        val encodedPath: String
        fun queryParam(key: String): String?
        fun queryParams(key: String): List<String?>
        fun header(key: String): String?
    }

    companion object
}

@JvmInline
private value class RequestImpl(private val request: RecordedRequest) : TestDispatcher.Request {
    override val encodedPath: String get() = request.url.encodedPath
    override fun queryParam(key: String): String? = request.url.queryParameter(key)
    override fun queryParams(key: String): List<String?> = request.url.queryParameterValues(key)
    override fun header(key: String): String? = request.headers[key]
}
