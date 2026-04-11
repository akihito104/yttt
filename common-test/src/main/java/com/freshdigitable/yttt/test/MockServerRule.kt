package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.di.OkHttpModule
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.MockServerDispatcher.ExpectedResponse
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

class MockServerRule(
    private val mockServerDispatcher: MockServerDispatcher = MockServerDispatcher.create(),
    private val onServerStart: (URL) -> Unit,
) : TestWatcher() {
    companion object {
        private val ZONE_ID_GMT: ZoneId = ZoneId.of("GMT")
        private val currentDate: String
            get() = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZONE_ID_GMT)
                .format(FakeDateTimeProviderModule.instant)
    }

    fun addResponses(vararg res: ExpectedResponse) {
        mockServerDispatcher.add(*res)
    }

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                logD { "dispatch: ${request.url}" }
                val req = RequestImpl(request)
                val res = mockServerDispatcher.dispatch(req).also {
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

    override fun starting(description: Description) {
        OkHttpModule.logLevel = HttpLoggingInterceptor.Level.NONE
        server.start()
        val serverUrl = server.url("").toUrl()
        onServerStart(serverUrl)
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

interface MockServerDispatcher {
    fun add(vararg res: ExpectedResponse)
    fun dispatch(request: Request): ResponseJson

    interface Request {
        val key: RequestKey
        val encodedPath: String
        val encodedQuery: String?
        fun queryParam(key: String): String?
        fun queryParams(key: String): List<String?>
        fun header(key: String): String?

        companion object {
            const val HEADER_IF_NONE_MATCH = "If-None-Match"
        }
    }

    interface ExpectedResponse : ResponseJson {
        val key: RequestKey
        val body: String get() = toString()

        companion object
    }

    data class RequestKey(
        val encodedPath: String,
        val encodedQuery: String? = null,
        val eTag: String? = null,
    ) {
        override fun toString(): String {
            val header = eTag?.let { "${Request.HEADER_IF_NONE_MATCH}=$eTag;" } ?: ""
            return "$header$encodedPath?$encodedQuery"
        }
    }

    companion object {
        internal fun create(): MockServerDispatcher = MockServerDispatcherImpl()
    }
}

@JvmInline
private value class RequestImpl(private val request: RecordedRequest) : MockServerDispatcher.Request {
    override val key: MockServerDispatcher.RequestKey
        get() = MockServerDispatcher.RequestKey(
            encodedPath = request.url.encodedPath,
            encodedQuery = request.url.encodedQuery,
            eTag = request.headers[MockServerDispatcher.Request.HEADER_IF_NONE_MATCH],
        )
    override val encodedPath: String get() = request.url.encodedPath
    override val encodedQuery: String? get() = request.url.encodedQuery
    override fun queryParam(key: String): String? = request.url.queryParameter(key)
    override fun queryParams(key: String): List<String?> = request.url.queryParameterValues(key)
    override fun header(key: String): String? = request.headers[key]
    override fun toString(): String = key.toString()
}

private class MockServerDispatcherImpl : MockServerDispatcher {
    private val expectedResponse = mutableMapOf<MockServerDispatcher.RequestKey, ExpectedResponse>()

    override fun add(vararg res: ExpectedResponse) {
        expectedResponse.putAll(res.map { it.key to it })
    }

    override fun dispatch(request: MockServerDispatcher.Request): ResponseJson {
        val key = request.key
        return expectedResponse.remove(key) ?: throw AssertionError("unexpected: $key, table: ${expectedResponse.keys}")
    }
}
