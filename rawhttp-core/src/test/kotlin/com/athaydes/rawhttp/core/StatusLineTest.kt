package com.athaydes.rawhttp.core

import com.athaydes.rawhttp.core.errors.InvalidHttpResponse
import io.kotlintest.matchers.fail
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

class StatusLineTest : StringSpec({

    "Can parse legal status-line (allow missing HTTP version)" {
        val table = table(headers("Status Line", "Expected version", "Expected status code", "Expected phrase"),
                row("200", HttpVersion.HTTP_1_1, 200, ""),
                row("200 OK", HttpVersion.HTTP_1_1, 200, "OK"),
                row("404 Not Found", HttpVersion.HTTP_1_1, 404, "Not Found"),
                row("500 Internal Server Error", HttpVersion.HTTP_1_1, 500, "Internal Server Error"),
                row("HTTP/1.0 200", HttpVersion.HTTP_1_0, 200, ""),
                row("HTTP/1.0 200 OK", HttpVersion.HTTP_1_0, 200, "OK"),
                row("HTTP/1.1 404", HttpVersion.HTTP_1_1, 404, ""),
                row("HTTP/1.1 500 Server Error", HttpVersion.HTTP_1_1, 500, "Server Error"),
                row("HTTP/1.0 200 My Custom Error Phrase", HttpVersion.HTTP_1_0, 200, "My Custom Error Phrase"),
                row("HTTP/0.9 200", HttpVersion.HTTP_0_9, 200, ""),
                row("HTTP/0.9 200 OK", HttpVersion.HTTP_0_9, 200, "OK"))

        forAll(table) { statusLine, expectedVersion, expectedStatusCode, expectedPhrase ->
            try {
                RawHttp.parseStatusLine(statusLine, true).run {
                    httpVersion shouldBe expectedVersion
                    statusCode shouldBe expectedStatusCode
                    reason shouldBe expectedPhrase
                }
            } catch (e: InvalidHttpResponse) {
                fail(e.toString())
            }
        }
    }

    "Cannot parse illegal status-line (allow missing HTTP version)" {
        val table = table(headers("Status Line", "Expected error"),
                row("", "Empty status line"),
                row("OK", "Invalid status code"),
                row("Accept: application/json", "Invalid status code"),
                row("OK 200", "Invalid status code"),
                row("HTTP/1.1 OK", "Invalid status code"),
                row("HTTP/1.2 200 OK", "Invalid HTTP version"))

        forAll(table) { statusLine, expectedError ->
            val error = shouldThrow<InvalidHttpResponse> { RawHttp.parseStatusLine(statusLine, true) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe 1
        }
    }

    "Can parse legal status-line (strict)" {
        val table = table(headers("Status Line", "Expected version", "Expected status code", "Expected phrase"),
                row("HTTP/1.0 200 OK", HttpVersion.HTTP_1_0, 200, "OK"),
                row("HTTP/1.1 404", HttpVersion.HTTP_1_1, 404, ""),
                row("HTTP/1.1 500 Server Error", HttpVersion.HTTP_1_1, 500, "Server Error"),
                row("HTTP/1.0 200 My Custom Error Phrase", HttpVersion.HTTP_1_0, 200, "My Custom Error Phrase"),
                row("HTTP/0.9 200", HttpVersion.HTTP_0_9, 200, ""),
                row("HTTP/0.9 200 OK", HttpVersion.HTTP_0_9, 200, "OK"))

        forAll(table) { statusLine, expectedVersion, expectedStatusCode, expectedPhrase ->
            try {
                RawHttp.parseStatusLine(statusLine, false).run {
                    httpVersion shouldBe expectedVersion
                    statusCode shouldBe expectedStatusCode
                    reason shouldBe expectedPhrase
                }
            } catch (e: InvalidHttpResponse) {
                fail(e.toString())
            }
        }
    }

    "Cannot parse illegal status-line (strict)" {
        val table = table(headers("Status Line", "Expected error"),
                row("", "Empty status line"),
                row("OK", "Missing HTTP version"),
                row("Accept: application/json", "Missing HTTP version"),
                row("OK 200", "Missing HTTP version"),
                row("HTTP/1.2 200 OK", "Invalid HTTP version"),
                row("HTTP/1.1 OK", "Invalid status code"),
                row("HTTP/1.1 OK 200", "Invalid status code"),
                row("200", "Missing HTTP version"),
                row("200 OK", "Missing HTTP version"),
                row("404 Not Found", "Missing HTTP version"))

        forAll(table) { statusLine, expectedError ->
            val error = shouldThrow<InvalidHttpResponse> { RawHttp.parseStatusLine(statusLine, false) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe 1
        }
    }
})
