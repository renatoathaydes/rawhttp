package com.athaydes.rawhttp.reqinedit

import com.athaydes.rawhttp.reqinedit.js.JsEnvironment
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.HttpVersion
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.RequestLine
import rawhttp.core.StatusLine
import rawhttp.core.body.StringBody
import rawhttp.core.client.RawHttpClient
import java.net.URI

class ReqInEditUnitTest {

    val http = RawHttp()

    private val fakeResponseStorage = ResponseStorage { _, _ ->
        throw UnsupportedOperationException("cannot store response")
    }

    private val fakeTestsReporter = HttpTestsReporter { }

    private val fakeFileReader = FileReader { throw UnsupportedOperationException() }

    @Test
    fun canRunSingleRequest() {
        val httpEnv = JsEnvironment()

        val response = RawHttpResponse<Unit>(
            null, null,
            StatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
            RawHttpHeaders.newBuilder()
                .with("Content-Type", "application/json")
                .build(),
            StringBody("""{ "foo": "bar" }""").toBodyReader()
        ).eagerly()

        val receivedRequests = mutableListOf<RawHttpRequest>()

        val fakeClient = RawHttpClient { req ->
            receivedRequests.add(req.eagerly())
            response
        }

        val request = RawHttpRequest(
            RequestLine("GET", URI.create("http://hello.com/foo/bar"), HttpVersion.HTTP_1_1),
            RawHttpHeaders.newBuilder()
                .with("Accept", "application/json")
                .with("Host", "hello.com")
                .build(),
            null, null
        ).eagerly()

        val unit = ReqInEditUnit(httpEnv, http, fakeClient)

        unit.run(listOf(ReqInEditEntry(request.toString(), emptyList(), null, null)))

        receivedRequests shouldBe listOf(request)
    }

    @Test
    fun canRunSingleRequestWithResponseHandler() {
        val httpEnv = JsEnvironment()

        val response = RawHttpResponse<Unit>(
            null, null,
            StatusLine(HttpVersion.HTTP_1_1, 404, "Not Found"),
            RawHttpHeaders.newBuilder()
                .with("Content-Type", "application/json")
                .build(),
            StringBody("""{ "error": "not here" }""").toBodyReader()
        ).eagerly()

        val fakeClient = RawHttpClient { response }

        val request = RawHttpRequest(
            RequestLine("GET", URI.create("http://hello.com/bar"), HttpVersion.HTTP_1_1),
            RawHttpHeaders.newBuilder()
                .with("Accept", "application/json")
                .with("Host", "hello.com")
                .build(),
            null, null
        ).eagerly()

        val script = """
            client.test("check status", function() {
                client.assert(response.status == 404);
            });
            client.test("check body", function() {
                client.assert(response.body.error == "not found");
            });
            client.test("check header", function() {
                var contentType = response.headers.valueOf('Content-Type');
                client.assert(contentType == 'wrong', 'content type is not wrong: ' + contentType);
            });
        """.trimIndent()

        val results = mutableListOf<HttpTestResult>()
        val testsReporter = HttpTestsReporter { result -> results.add(result) }

        val unit = ReqInEditUnit(httpEnv, http, fakeClient, fakeFileReader, fakeResponseStorage, testsReporter)

        var beforeTestsTime = System.currentTimeMillis()

        unit.run(listOf(ReqInEditEntry(request.toString(), emptyList(), StringOrFile.ofString(script), null)))

        results.size shouldBe 3

        results[0].name shouldBe "check status"
        results[0].isSuccess shouldBe true
        results[0] shouldEndAfterStartAnd beforeTestsTime

        beforeTestsTime = results[0].endTime

        results[1].name shouldBe "check body"
        results[1].isSuccess shouldBe false
        results[1].error shouldBe "assertion failed"
        results[1] shouldEndAfterStartAnd beforeTestsTime

        beforeTestsTime = results[1].endTime

        results[2].name shouldBe "check header"
        results[2].isSuccess shouldBe false
        results[2].error shouldBe "content type is not wrong: application/json"
        results[2] shouldEndAfterStartAnd beforeTestsTime
    }


    @Test
    fun canStoreHttpResponseInStorage() {
        val httpEnv = JsEnvironment()
        var storedResponse: RawHttpResponse<*>? = null
        var storedResponseRef: String? = null

        val responseStorage = ResponseStorage { response, responseRef ->
            if (storedResponse != null) throw IllegalArgumentException("Tried to store more than one response")
            storedResponse = response
            storedResponseRef = responseRef
        }

        val response = RawHttpResponse<Unit>(
            null, null,
            StatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
            RawHttpHeaders.newBuilder()
                .with("Content-Type", "application/json")
                .build(),
            StringBody("""{ "foo": "bar" }""").toBodyReader()
        ).eagerly()

        val fakeClient = RawHttpClient { response }

        val unit = ReqInEditUnit(httpEnv, http, fakeClient, fakeFileReader, responseStorage, fakeTestsReporter)

        unit.run(listOf(ReqInEditEntry("GET http://hello.com/", emptyList(), null, "my-response.http")))

        storedResponse shouldBe response
        storedResponseRef shouldBe "my-response.http"
    }

    private infix fun HttpTestResult.shouldEndAfterStartAnd(beforeTestsTime: Long) {
        startTime shouldBeInRange (beforeTestsTime..System.currentTimeMillis())
        endTime shouldBeInRange (startTime..System.currentTimeMillis())
    }

}